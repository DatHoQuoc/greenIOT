package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.DayOfWeekCode;
import com.greeniot.greensense.entity.enums.ScheduleRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** ENTITY — the "Lịch tưới" plan: which days, what time, how long. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "irrigation_schedules")
@CompoundIndex(name = "ix_schedule_due", def = "{'enabled':1,'nextRunAt':1}")
public class IrrigationSchedule extends BaseDocument {

    @Indexed
    private String gardenId;

    private String actuatorId;

    private String name;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private Set<DayOfWeekCode> daysOfWeek = EnumSet.allOf(DayOfWeekCode.class);

    /** Local time in the garden's timezone. */
    private LocalTime startTime;

    @Builder.Default
    private Integer durationMinutes = 15;

    /** Skip the run when the soil is already wetter than this (percent). Null disables the check. */
    private Double skipIfSoilMoistureAbove;

    @Builder.Default
    private boolean skipIfRainForecast = false;

    private Instant nextRunAt;

    private Instant lastRunAt;

    private ScheduleRunStatus lastRunStatus;

    private String lastSkipReason;
}
