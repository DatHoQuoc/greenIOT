package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.ScheduleDtos;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.control.weather.WeatherPort;
import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.IrrigationSchedule;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.DayOfWeekCode;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.ScheduleRunStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.IrrigationScheduleRepository;
import com.greeniot.greensense.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.OptionalDouble;

/**
 * CONTROL — the "Lịch tưới" plan.
 *
 * <p>A minute ticker picks up due schedules, applies the skip checks, then drives the pump
 * through {@link ActuatorControl} so that the same safety rules and audit trail apply as
 * for any other command. {@code nextRunAt} is always recomputed, even for a skipped run,
 * so a schedule can never get stuck in the past.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationScheduleControl {

    private final IrrigationScheduleRepository scheduleRepository;
    private final GardenRepository gardenRepository;
    private final ActuatorRepository actuatorRepository;
    private final SensorRepository sensorRepository;
    private final ActuatorControl actuatorControl;
    private final AutomationEventControl eventControl;
    private final WeatherPort weatherPort;

    @Transactional(readOnly = true)
    public List<ScheduleDtos.ScheduleResponse> list(String gardenId) {
        return scheduleRepository.findByGardenId(gardenId).stream()
                .map(ScheduleDtos.ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleDtos.ScheduleResponse create(Garden garden, ScheduleDtos.SaveScheduleRequest request) {
        // Fail fast if the pump does not belong to this garden.
        actuatorControl.require(garden.getId(), request.actuatorId());

        IrrigationSchedule schedule = IrrigationSchedule.builder()
                .gardenId(garden.getId())
                .actuatorId(request.actuatorId())
                .name(request.name())
                .enabled(request.enabled() == null || request.enabled())
                .daysOfWeek(request.daysOfWeek())
                .startTime(request.startTime())
                .durationMinutes(request.durationMinutes())
                .skipIfSoilMoistureAbove(request.skipIfSoilMoistureAbove())
                .skipIfRainForecast(Boolean.TRUE.equals(request.skipIfRainForecast()))
                .build();

        schedule.setNextRunAt(computeNextRun(schedule, zoneOf(garden), Instant.now()));
        return ScheduleDtos.ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleDtos.ScheduleResponse update(Garden garden, String scheduleId,
                                                ScheduleDtos.SaveScheduleRequest request) {
        IrrigationSchedule schedule = require(garden.getId(), scheduleId);
        actuatorControl.require(garden.getId(), request.actuatorId());

        schedule.setName(request.name());
        schedule.setActuatorId(request.actuatorId());
        schedule.setEnabled(request.enabled() == null || request.enabled());
        schedule.setDaysOfWeek(request.daysOfWeek());
        schedule.setStartTime(request.startTime());
        schedule.setDurationMinutes(request.durationMinutes());
        schedule.setSkipIfSoilMoistureAbove(request.skipIfSoilMoistureAbove());
        schedule.setSkipIfRainForecast(Boolean.TRUE.equals(request.skipIfRainForecast()));
        schedule.setNextRunAt(computeNextRun(schedule, zoneOf(garden), Instant.now()));

        return ScheduleDtos.ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(String gardenId, String scheduleId) {
        scheduleRepository.delete(require(gardenId, scheduleId));
    }

    @Transactional
    public ScheduleDtos.ScheduleResponse runNow(Garden garden, String scheduleId) {
        IrrigationSchedule schedule = require(garden.getId(), scheduleId);
        execute(schedule, garden, true);
        return ScheduleDtos.ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    /** Minute ticker: run everything that has come due. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        for (IrrigationSchedule schedule : scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(now)) {
            try {
                Garden garden = gardenRepository.findById(schedule.getGardenId()).orElse(null);
                if (garden == null) {
                    schedule.setEnabled(false);
                    scheduleRepository.save(schedule);
                    continue;
                }
                execute(schedule, garden, false);
                scheduleRepository.save(schedule);
            } catch (RuntimeException ex) {
                log.error("Schedule {} failed: {}", schedule.getId(), ex.getMessage(), ex);
                schedule.setLastRunStatus(ScheduleRunStatus.FAILED);
                schedule.setLastSkipReason(ex.getMessage());
                schedule.setNextRunAt(computeNextRun(schedule, ZoneId.of("Asia/Ho_Chi_Minh"), Instant.now()));
                scheduleRepository.save(schedule);
            }
        }
    }

    /**
     * @param manual a "run now" press bypasses the master switch and the moisture check —
     *               the user has explicitly asked for water
     */
    private void execute(IrrigationSchedule schedule, Garden garden, boolean manual) {
        Instant now = Instant.now();
        schedule.setLastRunAt(now);
        schedule.setNextRunAt(computeNextRun(schedule, zoneOf(garden), now));

        if (!manual && !garden.isSystemEnabled()) {
            skip(schedule, garden, "Hệ thống đang tắt");
            return;
        }

        if (!manual && schedule.getSkipIfSoilMoistureAbove() != null) {
            Double moisture = currentSoilMoisture(garden.getId());
            if (moisture != null && moisture > schedule.getSkipIfSoilMoistureAbove()) {
                skip(schedule, garden, "Độ ẩm đất %.0f%% đã trên ngưỡng %.0f%%"
                        .formatted(moisture, schedule.getSkipIfSoilMoistureAbove()));
                return;
            }
        }

        if (!manual && schedule.isSkipIfRainForecast() && weatherPort.isRainLikelySoon(garden)) {
            skip(schedule, garden, "Dự báo sắp mưa");
            return;
        }

        Actuator pump = actuatorRepository.findByIdAndGardenId(schedule.getActuatorId(), garden.getId())
                .orElse(null);
        if (pump == null) {
            skip(schedule, garden, "Không tìm thấy máy bơm");
            return;
        }

        var accepted = actuatorControl.command(pump, CommandType.TURN_ON, schedule.getDurationMinutes(),
                TriggerSource.SCHEDULE, schedule.getId(),
                "theo lịch %s, %d phút".formatted(schedule.getName(), schedule.getDurationMinutes()));

        if (accepted == null) {
            skip(schedule, garden, "Máy bơm không sẵn sàng");
            return;
        }

        schedule.setLastRunStatus(ScheduleRunStatus.SUCCESS);
        schedule.setLastSkipReason(null);
    }

    private void skip(IrrigationSchedule schedule, Garden garden, String reason) {
        schedule.setLastRunStatus(ScheduleRunStatus.SKIPPED);
        schedule.setLastSkipReason(reason);

        eventControl.record(com.greeniot.greensense.entity.AutomationEvent.builder()
                .gardenId(garden.getId())
                .occurredAt(Instant.now())
                .source(TriggerSource.SCHEDULE)
                .category(EventCategory.CHECK)
                .title("Bỏ qua lịch tưới: " + schedule.getName())
                .detail(reason)
                .tone(EventTone.GRAY)
                .scheduleId(schedule.getId())
                .build());
    }

    /** Mean of every soil probe in the plot, or null when none has reported yet. */
    private Double currentSoilMoisture(String gardenId) {
        OptionalDouble average = sensorRepository
                .findByGardenIdAndType(gardenId, SensorType.SOIL_MOISTURE).stream()
                .map(Sensor::getLastValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    /** Next occurrence of {@code startTime} on one of {@code daysOfWeek}, in the garden's zone. */
    public static Instant computeNextRun(IrrigationSchedule schedule, ZoneId zone, Instant after) {
        if (schedule.getStartTime() == null || schedule.getDaysOfWeek() == null
                || schedule.getDaysOfWeek().isEmpty()) {
            return null;
        }

        ZonedDateTime cursor = after.atZone(zone);
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = cursor.toLocalDate().plusDays(offset);
            if (!schedule.getDaysOfWeek().contains(DayOfWeekCode.from(date.getDayOfWeek()))) {
                continue;
            }
            ZonedDateTime candidate = LocalDateTime.of(date, schedule.getStartTime()).atZone(zone);
            if (candidate.toInstant().isAfter(after)) {
                return candidate.toInstant();
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public IrrigationSchedule require(String gardenId, String scheduleId) {
        return scheduleRepository.findByIdAndGardenId(scheduleId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("IrrigationSchedule", scheduleId));
    }

    private static ZoneId zoneOf(Garden garden) {
        try {
            return ZoneId.of(garden.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
