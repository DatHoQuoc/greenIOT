package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.IrrigationSchedule;
import com.greeniot.greensense.entity.enums.DayOfWeekCode;
import com.greeniot.greensense.entity.enums.ScheduleRunStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record SaveScheduleRequest(
            @NotBlank String name,
            @NotBlank String actuatorId,
            Boolean enabled,
            @NotEmpty Set<DayOfWeekCode> daysOfWeek,
            @NotNull LocalTime startTime,
            @NotNull @Min(1) Integer durationMinutes,
            Double skipIfSoilMoistureAbove,
            Boolean skipIfRainForecast) {
    }

    public record ScheduleResponse(
            String id,
            String gardenId,
            String actuatorId,
            String name,
            boolean enabled,
            Set<DayOfWeekCode> daysOfWeek,
            LocalTime startTime,
            Integer durationMinutes,
            Double skipIfSoilMoistureAbove,
            boolean skipIfRainForecast,
            Instant nextRunAt,
            Instant lastRunAt,
            ScheduleRunStatus lastRunStatus,
            String lastSkipReason) {

        public static ScheduleResponse from(IrrigationSchedule schedule) {
            return new ScheduleResponse(
                    schedule.getId(),
                    schedule.getGardenId(),
                    schedule.getActuatorId(),
                    schedule.getName(),
                    schedule.isEnabled(),
                    schedule.getDaysOfWeek(),
                    schedule.getStartTime(),
                    schedule.getDurationMinutes(),
                    schedule.getSkipIfSoilMoistureAbove(),
                    schedule.isSkipIfRainForecast(),
                    schedule.getNextRunAt(),
                    schedule.getLastRunAt(),
                    schedule.getLastRunStatus(),
                    schedule.getLastSkipReason());
        }
    }
}
