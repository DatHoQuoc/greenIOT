package com.greeniot.greensense.control;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.IrrigationSchedule;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.DayOfWeekCode;
import com.greeniot.greensense.entity.enums.ScheduleRunStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.IrrigationScheduleRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** The watering scheduler: when it fires, and — more importantly — when it declines to. */
@IntegrationTest
class IrrigationScheduleControlTest {

    private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private IrrigationScheduleControl scheduleControl;
    @Autowired private IrrigationScheduleRepository scheduleRepository;
    @Autowired private GardenRepository gardenRepository;
    @Autowired private ActuatorRepository actuatorRepository;
    @Autowired private SensorRepository sensorRepository;
    @Autowired private TestFixtures fixtures;

    private Garden garden;
    private Actuator pump;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
        garden = fixtures.garden(fixtures.user("owner@greensense.vn").getId());
        pump = fixtures.actuator(garden.getId(), "pump-1", ActuatorType.WATER_PUMP);
    }

    // ── computeNextRun ──────────────────────────────────────────────────────────

    @Test
    void nextRunPicksTodayWhenTheTimeHasNotPassedYet() {
        IrrigationSchedule schedule = schedule(LocalTime.of(23, 0), EnumSet.allOf(DayOfWeekCode.class));
        Instant after = ZonedDateTime.of(2026, 8, 12, 6, 0, 0, 0, SAIGON).toInstant();

        Instant next = IrrigationScheduleControl.computeNextRun(schedule, SAIGON, after);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 8, 12, 23, 0, 0, 0, SAIGON).toInstant());
    }

    @Test
    void nextRunRollsToTomorrowOnceTodaysSlotHasPassed() {
        IrrigationSchedule schedule = schedule(LocalTime.of(6, 0), EnumSet.allOf(DayOfWeekCode.class));
        Instant after = ZonedDateTime.of(2026, 8, 12, 7, 0, 0, 0, SAIGON).toInstant();

        Instant next = IrrigationScheduleControl.computeNextRun(schedule, SAIGON, after);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 8, 13, 6, 0, 0, 0, SAIGON).toInstant());
    }

    /** A Monday-only schedule evaluated on a Wednesday must skip forward five days. */
    @Test
    void nextRunHonoursTheDayFilter() {
        IrrigationSchedule schedule = schedule(LocalTime.of(6, 0), EnumSet.of(DayOfWeekCode.MON));
        ZonedDateTime wednesday = ZonedDateTime.of(2026, 8, 12, 7, 0, 0, 0, SAIGON);
        assertThat(wednesday.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);

        Instant next = IrrigationScheduleControl.computeNextRun(schedule, SAIGON, wednesday.toInstant());

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 8, 17, 6, 0, 0, 0, SAIGON).toInstant());
    }

    @Test
    void scheduleWithNoDaysHasNoNextRun() {
        IrrigationSchedule schedule = schedule(LocalTime.of(6, 0), EnumSet.noneOf(DayOfWeekCode.class));
        assertThat(IrrigationScheduleControl.computeNextRun(schedule, SAIGON, Instant.now())).isNull();
    }

    // ── tick() ──────────────────────────────────────────────────────────────────

    @Test
    void dueScheduleStartsThePump() {
        saveDueSchedule(null);

        scheduleControl.tick();

        assertThat(actuatorRepository.findById(pump.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.ON);
        assertThat(scheduleRepository.findAll().getFirst().getLastRunStatus())
                .isEqualTo(ScheduleRunStatus.SUCCESS);
    }

    @Test
    void wetSoilSkipsTheWateringAndSaysWhy() {
        Sensor soil = fixtures.sensor(garden.getId(), "soil-1", SensorType.SOIL_MOISTURE);
        soil.setLastValue(72d);
        sensorRepository.save(soil);

        saveDueSchedule(60d);

        scheduleControl.tick();

        assertThat(actuatorRepository.findById(pump.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.OFF);
        IrrigationSchedule after = scheduleRepository.findAll().getFirst();
        assertThat(after.getLastRunStatus()).isEqualTo(ScheduleRunStatus.SKIPPED);
        assertThat(after.getLastSkipReason()).contains("Độ ẩm đất");
    }

    @Test
    void masterSwitchOffSkipsTheWatering() {
        garden.setSystemEnabled(false);
        gardenRepository.save(garden);
        saveDueSchedule(null);

        scheduleControl.tick();

        assertThat(actuatorRepository.findById(pump.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.OFF);
        assertThat(scheduleRepository.findAll().getFirst().getLastSkipReason())
                .isEqualTo("Hệ thống đang tắt");
    }

    /**
     * The failure this guards against: a schedule that skipped kept its old
     * {@code nextRunAt}, so every subsequent tick re-ran it forever.
     */
    @Test
    void nextRunIsAlwaysAdvancedEvenWhenTheRunWasSkipped() {
        garden.setSystemEnabled(false);
        gardenRepository.save(garden);
        IrrigationSchedule schedule = saveDueSchedule(null);
        Instant staleNextRun = schedule.getNextRunAt();

        scheduleControl.tick();

        assertThat(scheduleRepository.findAll().getFirst().getNextRunAt()).isAfter(staleNextRun);
        assertThat(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(Instant.now())).isEmpty();
    }

    /** "Water now" is an explicit human instruction; it overrides both skip conditions. */
    @Test
    void runNowIgnoresTheMasterSwitchAndTheMoistureCheck() {
        Sensor soil = fixtures.sensor(garden.getId(), "soil-1", SensorType.SOIL_MOISTURE);
        soil.setLastValue(90d);
        sensorRepository.save(soil);

        garden.setSystemEnabled(false);
        gardenRepository.save(garden);
        IrrigationSchedule schedule = saveDueSchedule(60d);

        scheduleControl.runNow(garden, schedule.getId());

        assertThat(actuatorRepository.findById(pump.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.ON);
    }

    private IrrigationSchedule schedule(LocalTime start, EnumSet<DayOfWeekCode> days) {
        return IrrigationSchedule.builder()
                .gardenId(garden.getId())
                .actuatorId(pump.getId())
                .name("Tưới sáng")
                .enabled(true)
                .daysOfWeek(days)
                .startTime(start)
                .durationMinutes(15)
                .build();
    }

    private IrrigationSchedule saveDueSchedule(Double skipAboveMoisture) {
        IrrigationSchedule schedule = schedule(LocalTime.of(6, 0), EnumSet.allOf(DayOfWeekCode.class));
        schedule.setSkipIfSoilMoistureAbove(skipAboveMoisture);
        schedule.setNextRunAt(Instant.now().minusSeconds(60));
        return scheduleRepository.save(schedule);
    }
}
