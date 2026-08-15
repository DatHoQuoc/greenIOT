package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class SensorDtos {

    private SensorDtos() {
    }

    public record RegisterSensorRequest(
            @NotBlank String deviceCode,
            @NotBlank String channel,
            @NotNull SensorType type,
            String name,
            String unit,
            Integer samplingIntervalSec,
            Double calibrationOffset,
            Double calibrationScale) {
    }

    public record UpdateSensorRequest(
            String name,
            String unit,
            Integer samplingIntervalSec,
            Double calibrationOffset,
            Double calibrationScale,
            Boolean enabled) {
    }

    public record SensorResponse(
            String id,
            String gardenId,
            String deviceCode,
            String channel,
            SensorType type,
            String slug,
            String label,
            String name,
            String unit,
            SensorStatus status,
            Double lastValue,
            Instant lastReadingAt,
            Integer batteryLevel,
            String firmwareVersion,
            Integer samplingIntervalSec,
            boolean enabled) {

        public static SensorResponse from(Sensor sensor) {
            return new SensorResponse(
                    sensor.getId(),
                    sensor.getGardenId(),
                    sensor.getDeviceCode(),
                    sensor.getChannel(),
                    sensor.getType(),
                    sensor.getType().getSlug(),
                    sensor.getType().getLabel(),
                    sensor.getName(),
                    sensor.getUnit(),
                    sensor.getStatus(),
                    sensor.getLastValue(),
                    sensor.getLastReadingAt(),
                    sensor.getBatteryLevel(),
                    sensor.getFirmwareVersion(),
                    sensor.getSamplingIntervalSec(),
                    sensor.isEnabled());
        }
    }

    /** One card in the home-screen sensor grid, threshold state already resolved. */
    public record SensorTile(
            String sensorId,
            SensorType type,
            String slug,
            String label,
            Double value,
            String unit,
            SensorStatus status,
            Instant lastReadingAt,
            boolean breached,
            GardenDtos.ThresholdDto threshold) {

        public static SensorTile from(Sensor sensor, Threshold threshold) {
            boolean breached = threshold != null
                    && sensor.getLastValue() != null
                    && threshold.isBreached(sensor.getLastValue());

            return new SensorTile(
                    sensor.getId(),
                    sensor.getType(),
                    sensor.getType().getSlug(),
                    sensor.getType().getLabel(),
                    sensor.getLastValue(),
                    sensor.getUnit(),
                    sensor.getStatus(),
                    sensor.getLastReadingAt(),
                    breached,
                    GardenDtos.ThresholdDto.from(threshold));
        }
    }
}
