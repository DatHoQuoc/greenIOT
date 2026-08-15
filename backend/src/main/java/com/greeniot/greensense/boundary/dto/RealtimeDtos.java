package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.enums.SensorType;

import java.time.Instant;

/** Messages pushed to {@code /topic/garden/{gardenId}/...}. */
public final class RealtimeDtos {

    private RealtimeDtos() {
    }

    public record ReadingPush(
            String sensorId,
            SensorType type,
            String slug,
            double value,
            String unit,
            Instant timestamp,
            boolean breached) {
    }

    public record ActuatorPush(ActuatorDtos.ActuatorResponse actuator) {
    }

    public record AlertPush(AlertDtos.AlertResponse alert, long unreadCount) {
    }

    public record EventPush(EventDtos.EventResponse event) {
    }
}
