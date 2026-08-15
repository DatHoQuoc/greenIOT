package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.ReadingQuality;
import com.greeniot.greensense.entity.enums.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.TimeSeries;
import org.springframework.data.mongodb.core.timeseries.Granularity;

import java.time.Instant;

/**
 * ENTITY — a single measurement. Stored in a MongoDB <b>time-series</b> collection:
 * {@code timeField=timestamp}, {@code metaField=meta}, minute granularity, TTL 180 days.
 *
 * <p>The collection and its TTL are created by {@code MongoCollectionInitializer};
 * {@code @TimeSeries} alone does not apply {@code expireAfterSeconds}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "sensor_readings")
@TimeSeries(collection = "sensor_readings", timeField = "timestamp",
        metaField = "meta", granularity = Granularity.MINUTES)
public class SensorReading {

    @Id
    private String id;

    @Field("timestamp")
    private Instant timestamp;

    private Meta meta;

    private Double value;

    private String unit;

    @Builder.Default
    private ReadingQuality quality = ReadingQuality.GOOD;

    /** Metadata field of the time-series collection — indexed as one unit by Mongo. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Meta {
        private String gardenId;
        private String sensorId;
        private SensorType type;
    }
}
