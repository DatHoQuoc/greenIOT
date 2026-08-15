package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.GardenDtos;
import com.greeniot.greensense.boundary.dto.ReadingDtos;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.SensorReadingRepository;
import com.greeniot.greensense.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * CONTROL — history and statistics behind the chart screens.
 *
 * <p>The named ranges match the frontend's selector: {@code 24H}, {@code 7D}, {@code 30D}.
 * Each range picks a bucket width that keeps a chart around 24–60 points regardless of how
 * fast the sensors publish.
 */
@Service
@RequiredArgsConstructor
public class ReadingAnalyticsControl {

    private final SensorReadingRepository readingRepository;
    private final SensorRepository sensorRepository;

    /** Named range to (duration, bucket width in minutes). */
    public enum Range {
        H24(Duration.ofHours(24), 60),
        D7(Duration.ofDays(7), 180),
        D30(Duration.ofDays(30), 720);

        private final Duration duration;
        private final int bucketMinutes;

        Range(Duration duration, int bucketMinutes) {
            this.duration = duration;
            this.bucketMinutes = bucketMinutes;
        }

        public Duration getDuration() {
            return duration;
        }

        public int getBucketMinutes() {
            return bucketMinutes;
        }

        public static Range parse(String raw) {
            if (raw == null) {
                return H24;
            }
            return switch (raw.trim().toUpperCase().replace(" ", "")) {
                case "24H", "H24", "1D" -> H24;
                case "7D", "D7", "7NGAY", "7NGÀY" -> D7;
                case "30D", "D30", "30NGAY", "30NGÀY" -> D30;
                default -> throw new IllegalArgumentException("Unsupported range: " + raw);
            };
        }
    }

    @Transactional(readOnly = true)
    public ReadingDtos.SeriesResponse series(String gardenId, SensorType type, String rangeLabel,
                                             Instant customFrom, Instant customTo) {
        Instant to = customTo != null ? customTo : Instant.now();
        Instant from;
        int bucketMinutes;

        if (customFrom != null) {
            from = customFrom;
            bucketMinutes = bucketFor(Duration.between(from, to));
        } else {
            Range range = Range.parse(rangeLabel);
            from = to.minus(range.getDuration());
            bucketMinutes = range.getBucketMinutes();
        }

        List<ReadingDtos.SeriesPoint> points = readingRepository
                .bucketedSeries(gardenId, type, from, to, bucketMinutes).stream()
                .map(point -> new ReadingDtos.SeriesPoint(
                        point.timestamp(),
                        round(point.avg()),
                        round(point.min()),
                        round(point.max()),
                        point.count()))
                .toList();

        return new ReadingDtos.SeriesResponse(
                type, unitFor(gardenId, type), rangeLabel, from, to, bucketMinutes, points);
    }

    /**
     * Current value plus min/max/avg for the window, and the delta against the immediately
     * preceding window of the same length — that is the "↑ Tăng 2°C so với hôm qua" line.
     */
    @Transactional(readOnly = true)
    public ReadingDtos.SummaryResponse summary(Garden garden, SensorType type, String rangeLabel) {
        Range range = Range.parse(rangeLabel);
        Instant to = Instant.now();
        Instant from = to.minus(range.getDuration());
        Instant previousFrom = from.minus(range.getDuration());

        var current = readingRepository.stats(garden.getId(), type, from, to);
        var previous = readingRepository.stats(garden.getId(), type, previousFrom, from);

        Sensor sensor = sensorRepository.findByGardenIdAndType(garden.getId(), type).stream()
                .findFirst()
                .orElse(null);

        Double latest = sensor == null ? null : sensor.getLastValue();
        Double previousAvg = previous == null ? null : round(previous.avg());
        Double delta = null;
        Double deltaPercent = null;
        String trend = "flat";

        if (current != null && previousAvg != null) {
            delta = round(current.avg() - previousAvg);
            if (previousAvg != 0d) {
                deltaPercent = round(delta / Math.abs(previousAvg) * 100d);
            }
            trend = delta > 0 ? "up" : delta < 0 ? "down" : "flat";
        }

        return new ReadingDtos.SummaryResponse(
                type,
                sensor == null ? type.getDefaultUnit() : sensor.getUnit(),
                rangeLabel,
                latest,
                current == null ? null : round(current.min()),
                current == null ? null : round(current.max()),
                current == null ? null : round(current.avg()),
                current == null ? 0 : current.count(),
                previousAvg,
                delta,
                deltaPercent,
                trend,
                GardenDtos.ThresholdDto.from(garden.thresholdFor(type)));
    }

    @Transactional(readOnly = true)
    public List<ReadingDtos.ReadingResponse> latestPerSensor(String gardenId) {
        return sensorRepository.findByGardenId(gardenId).stream()
                .map(sensor -> readingRepository
                        .findFirstByMetaSensorIdOrderByTimestampDesc(sensor.getId())
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(ReadingDtos.ReadingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.greeniot.greensense.entity.SensorReading> raw(String gardenId, SensorType type,
                                                                  Instant from, Instant to) {
        return readingRepository.findGardenSeries(gardenId, type, from, to);
    }

    private String unitFor(String gardenId, SensorType type) {
        return sensorRepository.findByGardenIdAndType(gardenId, type).stream()
                .findFirst()
                .map(Sensor::getUnit)
                .orElse(type.getDefaultUnit());
    }

    /** Keeps custom ranges to a readable number of points. */
    private static int bucketFor(Duration span) {
        long hours = Math.max(1, span.toHours());
        if (hours <= 6) {
            return 15;
        }
        if (hours <= 48) {
            return 60;
        }
        if (hours <= 24 * 14) {
            return 180;
        }
        return 720;
    }

    static Sensor requireSensor(SensorRepository repository, String gardenId, String sensorId) {
        return repository.findByIdAndGardenId(sensorId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
