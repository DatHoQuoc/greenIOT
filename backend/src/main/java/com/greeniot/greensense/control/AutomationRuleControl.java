package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.RuleDtos;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CONTROL — CRUD for automation rules. Evaluation itself lives in {@link RuleEngineControl}. */
@Service
@RequiredArgsConstructor
public class AutomationRuleControl {

    private final AutomationRuleRepository ruleRepository;
    private final ActuatorControl actuatorControl;

    @Transactional(readOnly = true)
    public List<RuleDtos.RuleResponse> list(String gardenId) {
        return ruleRepository.findByGardenId(gardenId).stream()
                .map(RuleDtos.RuleResponse::from)
                .toList();
    }

    @Transactional
    public RuleDtos.RuleResponse create(String gardenId, RuleDtos.SaveRuleRequest request) {
        validate(gardenId, request);

        AutomationRule rule = AutomationRule.builder()
                .gardenId(gardenId)
                .name(request.name())
                .enabled(request.enabled() == null || request.enabled())
                .priority(request.priority() == null ? 100 : request.priority())
                .condition(request.condition().toEntity())
                .action(request.action().toEntity())
                .cooldownMinutes(request.cooldownMinutes() == null ? 15 : request.cooldownMinutes())
                .activeFrom(request.activeFrom())
                .activeTo(request.activeTo())
                .build();

        return RuleDtos.RuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public RuleDtos.RuleResponse update(String gardenId, String ruleId, RuleDtos.SaveRuleRequest request) {
        validate(gardenId, request);
        AutomationRule rule = require(gardenId, ruleId);

        rule.setName(request.name());
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setPriority(request.priority() == null ? rule.getPriority() : request.priority());
        rule.setCondition(request.condition().toEntity());
        rule.setAction(request.action().toEntity());
        rule.setCooldownMinutes(request.cooldownMinutes() == null
                ? rule.getCooldownMinutes() : request.cooldownMinutes());
        rule.setActiveFrom(request.activeFrom());
        rule.setActiveTo(request.activeTo());
        // Editing a rule invalidates any in-flight sustain streak.
        rule.setConditionHoldingSince(null);

        return RuleDtos.RuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public RuleDtos.RuleResponse setEnabled(String gardenId, String ruleId, boolean enabled) {
        AutomationRule rule = require(gardenId, ruleId);
        rule.setEnabled(enabled);
        if (!enabled) {
            rule.setConditionHoldingSince(null);
        }
        return RuleDtos.RuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(String gardenId, String ruleId) {
        ruleRepository.delete(require(gardenId, ruleId));
    }

    private void validate(String gardenId, RuleDtos.SaveRuleRequest request) {
        RuleDtos.ActionDto action = request.action();
        if (action.actuatorId() == null && action.actuatorType() == null) {
            throw new BusinessRuleException("RULE_NO_TARGET",
                    "A rule must target either an actuator id or an actuator type");
        }
        if (action.actuatorId() != null) {
            // Throws if the actuator belongs to another garden.
            actuatorControl.require(gardenId, action.actuatorId());
        }

        RuleDtos.ConditionDto condition = request.condition();
        boolean needsSecond = condition.operator() == com.greeniot.greensense.entity.enums.RuleOperator.BETWEEN
                || condition.operator() == com.greeniot.greensense.entity.enums.RuleOperator.OUTSIDE;
        if (needsSecond && condition.secondValue() == null) {
            throw new BusinessRuleException("RULE_MISSING_BOUND",
                    condition.operator() + " requires secondValue as the upper bound");
        }
        if (needsSecond && condition.secondValue() <= condition.value()) {
            throw new BusinessRuleException("RULE_BAD_BOUND", "secondValue must be greater than value");
        }
    }

    @Transactional(readOnly = true)
    public AutomationRule require(String gardenId, String ruleId) {
        return ruleRepository.findByIdAndGardenId(ruleId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationRule", ruleId));
    }
}
