package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** ENTITY — a threshold breach or device fault. Drives the bell badge and the "Cảnh báo" tab. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "alerts")
@CompoundIndex(name = "ix_alert_inbox", def = "{'gardenId':1,'read':1,'raisedAt':-1}")
public class Alert extends BaseDocument {

    @Indexed
    private String gardenId;

    private String sensorId;

    private String actuatorId;

    private String ruleId;

    /** Stable machine code, e.g. SOIL_MOISTURE_LOW — used for de-duplication. */
    private String code;

    private AlertSeverity severity;

    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    private String title;

    private String message;

    private Double triggerValue;

    private Double thresholdValue;

    private String unit;

    @Builder.Default
    private boolean read = false;

    private Instant raisedAt;

    private Instant acknowledgedAt;

    private Instant resolvedAt;
}
