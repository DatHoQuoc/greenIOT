package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.SensorReading;
import com.greeniot.greensense.entity.enums.SensorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class ReadingDtos {

    private ReadingDtos() {
    }

    /** HTTP ingest fallback for nodes that cannot speak MQTT. */
    public record IngestRequest(
            @NotBlank String deviceCode,
            @NotBlank String channel,
            @NotNull Double value,
            Instant timestamp) {
    }

    public record IngestBatchRequest(@NotNull List<IngestRequest> readings) {
    }

    public record ReadingResponse(Instant timestamp, Double value, String unit, SensorType type, String sensorId) {

        public static ReadingResponse from(SensorReading reading) {
            return new ReadingResponse(
                    reading.getTimestamp(),
                    reading.getValue(),
                    reading.getUnit(),
                    reading.getMeta() == null ? null : reading.getMeta().getType(),
                    reading.getMeta() == null ? null : reading.getMeta().getSensorId());
        }
    }

    /** One down-sampled chart point. */
    public record SeriesPoint(Instant timestamp, double value, double min, double max, long samples) {
    }

    public record SeriesResponse(
            SensorType type,
            String unit,
            String range,
            Instant from,
            Instant to,
            int bucketMinutes,
            List<SeriesPoint> points) {
    }

    /**
     * Backs the sensor hero card: current value, the min/max footer and the
     * "↑ Tăng 2°C so với hôm qua" line (delta against the preceding equal-length window).
     */
    public record SummaryResponse(
            SensorType type,
            String unit,
            String range,
            Double current,
            Double min,
            Double max,
            Double average,
            long samples,
            Double previousAverage,
            Double delta,
            Double deltaPercent,
            String trend,
            GardenDtos.ThresholdDto threshold) {
    }
}
