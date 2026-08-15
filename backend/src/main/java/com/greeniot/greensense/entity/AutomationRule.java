package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.RuleOperator;
import com.greeniot.greensense.entity.enums.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

/**
 * ENTITY — "WHEN metric op value FOR n minutes THEN command actuator".
 *
 * <p>The timeline entry {@code "Quạt tản nhiệt tự động bật (nhiệt độ vượt 30°C)"} is
 * this rule: {@code TEMPERATURE GT 30 sustained 5m → FAN TURN_ON}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "automation_rules")
@CompoundIndex(name = "ix_rule_garden", def = "{'gardenId':1,'enabled':1}")
public class AutomationRule extends BaseDocument {

    @Indexed
    private String gardenId;

    private String name;

    @Builder.Default
    private boolean enabled = true;

    /** Lower number wins when two rules target the same actuator in one evaluation. */
    @Builder.Default
    private int priority = 100;

    private Condition condition;

    private Action action;

    /** Refuses to re-fire inside this window even if the condition still holds. */
    @Builder.Default
    private Integer cooldownMinutes = 15;

    /** Optional time-of-day window, e.g. only ventilate between 06:00 and 18:00. */
    private LocalTime activeFrom;

    private LocalTime activeTo;

    private Instant lastTriggeredAt;

    /** First instant the condition became true in the current streak; reset when it breaks. */
    private Instant conditionHoldingSince;

    @Builder.Default
    private long triggerCount = 0;

    public boolean isInCooldown(Instant now) {
        if (lastTriggeredAt == null || cooldownMinutes == null || cooldownMinutes <= 0) {
            return false;
        }
        return lastTriggeredAt.plus(Duration.ofMinutes(cooldownMinutes)).isAfter(now);
    }

    /** True when no window is configured or {@code time} falls inside it (wrap-around aware). */
    public boolean isWithinActiveWindow(LocalTime time) {
        if (activeFrom == null || activeTo == null) {
            return true;
        }
        if (activeFrom.isBefore(activeTo)) {
            return !time.isBefore(activeFrom) && !time.isAfter(activeTo);
        }
        // window crosses midnight, e.g. 22:00 -> 05:00
        return !time.isBefore(activeFrom) || !time.isAfter(activeTo);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Condition {
        private SensorType sensorType;
        private RuleOperator operator;
        private Double value;
        /** Upper bound for BETWEEN / OUTSIDE. */
        private Double secondValue;
        /** The condition must hold this long before the action fires; 0 = fire immediately. */
        @Builder.Default
        private Integer sustainedForMinutes = 0;

        public boolean matches(double reading) {
            return operator != null && value != null && operator.test(reading, value, secondValue);
        }

        /** Human-readable reason string used in the timeline detail line. */
        public String describe(String unit) {
            String u = unit == null ? "" : unit;
            return switch (operator) {
                case GT, GTE -> "vượt " + trim(value) + u;
                case LT, LTE -> "dưới " + trim(value) + u;
                case BETWEEN -> "trong khoảng " + trim(value) + "–" + trim(secondValue) + u;
                case OUTSIDE -> "ngoài khoảng " + trim(value) + "–" + trim(secondValue) + u;
            };
        }

        private static String trim(Double d) {
            if (d == null) {
                return "?";
            }
            return d == Math.floor(d) ? String.valueOf(d.intValue()) : String.valueOf(d);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Action {
        /** Target a specific device, or leave null and target every actuator of {@code actuatorType}. */
        private String actuatorId;
        private ActuatorType actuatorType;
        private CommandType command;
        /** Auto-off after this long; null means "stay until another rule turns it off". */
        private Integer durationMinutes;
        @Builder.Default
        private boolean raiseAlert = false;
        @Builder.Default
        private AlertSeverity alertSeverity = AlertSeverity.WARNING;
    }
}
