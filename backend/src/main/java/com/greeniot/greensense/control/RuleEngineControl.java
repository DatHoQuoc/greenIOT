package com.greeniot.greensense.control;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * CONTROL — evaluates automation rules against each incoming reading.
 *
 * <p>Guard order matters and is deliberate:
 * <ol>
 *   <li>garden master switch — one toggle stops all automation;</li>
 *   <li>time-of-day window;</li>
 *   <li>condition match — a miss clears the sustain streak;</li>
 *   <li>sustain duration — stops a single noisy sample from starting a pump;</li>
 *   <li>cooldown — stops oscillation around the threshold.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineControl {

    private final AutomationRuleRepository ruleRepository;
    private final ActuatorControl actuatorControl;
    private final AlertControl alertControl;

    @Transactional
    public void evaluate(Garden garden, Sensor sensor, double value) {
        if (!garden.isSystemEnabled()) {
            return;
        }

        List<AutomationRule> rules = ruleRepository
                .findByGardenIdAndEnabledTrueAndConditionSensorTypeOrderByPriorityAsc(
                        garden.getId(), sensor.getType());

        Instant now = Instant.now();
        LocalTime localNow = LocalTime.now(zoneOf(garden));

        for (AutomationRule rule : rules) {
            try {
                evaluateOne(rule, sensor, value, now, localNow);
            } catch (RuntimeException ex) {
                // One broken rule must not stop the others or the ingestion path.
                log.error("Rule {} failed to evaluate: {}", rule.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void evaluateOne(AutomationRule rule, Sensor sensor, double value,
                             Instant now, LocalTime localNow) {
        if (rule.getCondition() == null || rule.getAction() == null) {
            return;
        }
        if (!rule.isWithinActiveWindow(localNow)) {
            return;
        }

        if (!rule.getCondition().matches(value)) {
            if (rule.getConditionHoldingSince() != null) {
                rule.setConditionHoldingSince(null);
                ruleRepository.save(rule);
            }
            return;
        }

        // Condition is true — start or continue the streak.
        if (rule.getConditionHoldingSince() == null) {
            rule.setConditionHoldingSince(now);
            ruleRepository.save(rule);
        }

        int sustainMinutes = rule.getCondition().getSustainedForMinutes() == null
                ? 0 : rule.getCondition().getSustainedForMinutes();
        if (sustainMinutes > 0) {
            Instant readyAt = rule.getConditionHoldingSince().plus(Duration.ofMinutes(sustainMinutes));
            if (readyAt.isAfter(now)) {
                return;
            }
        }

        if (rule.isInCooldown(now)) {
            return;
        }

        fire(rule, sensor, value, now);
    }

    private void fire(AutomationRule rule, Sensor sensor, double value, Instant now) {
        AutomationRule.Action action = rule.getAction();
        String reason = rule.getCondition().describe(sensor.getUnit());

        // "nhiệt độ vượt 30°C" — the parenthetical the timeline shows under the title.
        String detail = sensor.getType().getLabel().toLowerCase() + " " + reason;

        if (action.getActuatorId() != null) {
            Actuator actuator = actuatorControl.require(rule.getGardenId(), action.getActuatorId());
            actuatorControl.command(actuator, action.getCommand(), action.getDurationMinutes(),
                    TriggerSource.RULE, rule.getId(), detail);
        } else if (action.getActuatorType() != null) {
            actuatorControl.commandByType(rule.getGardenId(), action.getActuatorType(), action.getCommand(),
                    action.getDurationMinutes(), TriggerSource.RULE, rule.getId(), detail);
        } else {
            log.warn("Rule {} has no actuator target", rule.getId());
            return;
        }

        if (action.isRaiseAlert()) {
            alertControl.raise(Alert.builder()
                    .gardenId(rule.getGardenId())
                    .sensorId(sensor.getId())
                    .ruleId(rule.getId())
                    .code("RULE_" + rule.getId())
                    .severity(action.getAlertSeverity())
                    .title(rule.getName())
                    .message(sensor.getType().getLabel() + " " + reason)
                    .triggerValue(value)
                    .thresholdValue(rule.getCondition().getValue())
                    .unit(sensor.getUnit())
                    .build());
        }

        rule.setLastTriggeredAt(now);
        rule.setTriggerCount(rule.getTriggerCount() + 1);
        rule.setConditionHoldingSince(null);
        ruleRepository.save(rule);

        log.info("Rule '{}' fired for garden {} ({} = {})",
                rule.getName(), rule.getGardenId(), sensor.getType(), value);
    }

    private static ZoneId zoneOf(Garden garden) {
        try {
            return ZoneId.of(garden.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
