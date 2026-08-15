package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.AlertStatus;

import java.time.Instant;

public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertResponse(
            String id,
            String gardenId,
            String sensorId,
            String actuatorId,
            String ruleId,
            String code,
            AlertSeverity severity,
            AlertStatus status,
            String title,
            String message,
            Double triggerValue,
            Double thresholdValue,
            String unit,
            boolean read,
            Instant raisedAt,
            Instant acknowledgedAt,
            Instant resolvedAt) {

        public static AlertResponse from(Alert alert) {
            return new AlertResponse(
                    alert.getId(),
                    alert.getGardenId(),
                    alert.getSensorId(),
                    alert.getActuatorId(),
                    alert.getRuleId(),
                    alert.getCode(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    alert.getTitle(),
                    alert.getMessage(),
                    alert.getTriggerValue(),
                    alert.getThresholdValue(),
                    alert.getUnit(),
                    alert.isRead(),
                    alert.getRaisedAt(),
                    alert.getAcknowledgedAt(),
                    alert.getResolvedAt());
        }
    }

    public record UnreadCountResponse(long unread) {
    }
}
