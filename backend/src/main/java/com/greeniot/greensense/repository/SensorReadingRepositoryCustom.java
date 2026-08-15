package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.enums.SensorType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Aggregation pipelines that Spring Data cannot derive from a method name. */
public interface SensorReadingRepositoryCustom {

    /** min / max / avg / count over a window; {@code null} when the window holds no data. */
    ReadingStats stats(String gardenId, SensorType type, Instant from, Instant to);

    /**
     * Down-samples the raw points into fixed buckets so a 30-day chart stays a few dozen
     * points instead of tens of thousands.
     *
     * @param bucketMinutes width of each bucket
     */
    List<BucketPoint> bucketedSeries(String gardenId, SensorType type, Instant from, Instant to, int bucketMinutes);

    /** Newest value per sensor for a whole garden — one round trip for the dashboard grid. */
    Map<String, LatestReading> latestPerSensor(String gardenId);

    record ReadingStats(double min, double max, double avg, long count) {}

    record BucketPoint(Instant timestamp, double avg, double min, double max, long count) {}

    record LatestReading(String sensorId, SensorType type, double value, String unit, Instant timestamp) {}
}
