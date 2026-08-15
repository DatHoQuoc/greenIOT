package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * ENTITY — one sensor's day, rolled up.
 *
 * <p>Raw readings live in a time-series collection with a 180-day TTL, so without this a
 * garden's history simply vanishes after six months. These rows are tiny (one per sensor
 * per day) and are never expired, which is what makes "compare this March to last March"
 * possible at all.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "sensor_daily_stats")
@CompoundIndex(name = "uk_daily_stat", def = "{'sensorId':1,'day':1}", unique = true)
@CompoundIndex(name = "ix_daily_stat_garden", def = "{'gardenId':1,'type':1,'day':-1}")
public class SensorDailyStat extends BaseDocument {

    @Indexed
    private String gardenId;

    private String sensorId;

    private SensorType type;

    /** Calendar day in the garden's timezone, not UTC. */
    private LocalDate day;

    private Double min;

    private Double max;

    private Double avg;

    private long samples;

    private String unit;
}
