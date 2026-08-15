package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.RuleOperator;
import com.greeniot.greensense.entity.enums.SensorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;

public final class RuleDtos {

    private RuleDtos() {
    }

    public record ConditionDto(
            @NotNull SensorType sensorType,
            @NotNull RuleOperator operator,
            @NotNull Double value,
            Double secondValue,
            Integer sustainedForMinutes) {

        public static ConditionDto from(AutomationRule.Condition condition) {
            return condition == null ? null : new ConditionDto(
                    condition.getSensorType(), condition.getOperator(), condition.getValue(),
                    condition.getSecondValue(), condition.getSustainedForMinutes());
        }

        public AutomationRule.Condition toEntity() {
            return AutomationRule.Condition.builder()
                    .sensorType(sensorType)
                    .operator(operator)
                    .value(value)
                    .secondValue(secondValue)
                    .sustainedForMinutes(sustainedForMinutes == null ? 0 : sustainedForMinutes)
                    .build();
        }
    }

    public record ActionDto(
            String actuatorId,
            ActuatorType actuatorType,
            @NotNull CommandType command,
            Integer durationMinutes,
            Boolean raiseAlert,
            AlertSeverity alertSeverity) {

        public static ActionDto from(AutomationRule.Action action) {
            return action == null ? null : new ActionDto(
                    action.getActuatorId(), action.getActuatorType(), action.getCommand(),
                    action.getDurationMinutes(), action.isRaiseAlert(), action.getAlertSeverity());
        }

        public AutomationRule.Action toEntity() {
            return AutomationRule.Action.builder()
                    .actuatorId(actuatorId)
                    .actuatorType(actuatorType)
                    .command(command)
                    .durationMinutes(durationMinutes)
                    .raiseAlert(Boolean.TRUE.equals(raiseAlert))
                    .alertSeverity(alertSeverity == null ? AlertSeverity.WARNING : alertSeverity)
                    .build();
        }
    }

    public record SaveRuleRequest(
            @NotBlank String name,
            Boolean enabled,
            Integer priority,
            @NotNull @Valid ConditionDto condition,
            @NotNull @Valid ActionDto action,
            Integer cooldownMinutes,
            LocalTime activeFrom,
            LocalTime activeTo) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record RuleResponse(
            String id,
            String gardenId,
            String name,
            boolean enabled,
            int priority,
            ConditionDto condition,
            ActionDto action,
            Integer cooldownMinutes,
            LocalTime activeFrom,
            LocalTime activeTo,
            Instant lastTriggeredAt,
            long triggerCount) {

        public static RuleResponse from(AutomationRule rule) {
            return new RuleResponse(
                    rule.getId(),
                    rule.getGardenId(),
                    rule.getName(),
                    rule.isEnabled(),
                    rule.getPriority(),
                    ConditionDto.from(rule.getCondition()),
                    ActionDto.from(rule.getAction()),
                    rule.getCooldownMinutes(),
                    rule.getActiveFrom(),
                    rule.getActiveTo(),
                    rule.getLastTriggeredAt(),
                    rule.getTriggerCount());
        }
    }
}
