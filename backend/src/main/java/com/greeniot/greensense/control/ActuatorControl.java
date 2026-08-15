package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.ActuatorDtos;
import com.greeniot.greensense.boundary.dto.DeviceDtos;
import com.greeniot.greensense.boundary.mqtt.MqttCommandPublisher;
import com.greeniot.greensense.boundary.ws.RealtimeBoundary;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.DeviceCommand;
import com.greeniot.greensense.entity.enums.ActuatorMode;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.CommandStatus;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.DeviceCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CONTROL — the actuator state machine.
 *
 * <p>Every state change goes through {@link #command}: it enforces the safety rules
 * (device enabled, MANUAL lock, cooldown), records a {@link DeviceCommand} for the ack
 * round-trip, publishes over MQTT, then optimistically reflects the new state so the UI
 * responds immediately. The real state is reconciled when the device acks, or the
 * command times out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActuatorControl {

    private final ActuatorRepository actuatorRepository;
    private final DeviceCommandRepository commandRepository;
    private final MqttCommandPublisher commandPublisher;
    private final RealtimeBoundary realtimeBoundary;
    private final AutomationEventControl eventControl;

    @Transactional(readOnly = true)
    public List<ActuatorDtos.ActuatorResponse> list(String gardenId) {
        return actuatorRepository.findByGardenId(gardenId).stream()
                .map(ActuatorDtos.ActuatorResponse::from)
                .toList();
    }

    @Transactional
    public ActuatorDtos.ActuatorResponse register(String gardenId, ActuatorDtos.RegisterActuatorRequest request) {
        actuatorRepository.findByGardenIdAndDeviceCodeAndChannel(
                gardenId, request.deviceCode(), request.channel()).ifPresent(existing -> {
            throw new BusinessRuleException("ACTUATOR_EXISTS",
                    "An actuator is already registered on " + request.deviceCode() + "/" + request.channel());
        });

        Actuator actuator = Actuator.builder()
                .gardenId(gardenId)
                .deviceCode(request.deviceCode())
                .channel(request.channel())
                .type(request.type())
                .name(StringUtils.hasText(request.name()) ? request.name() : request.type().getLabel())
                .maxRuntimeMinutes(request.maxRuntimeMinutes() == null ? 30 : request.maxRuntimeMinutes())
                .cooldownMinutes(request.cooldownMinutes() == null ? 5 : request.cooldownMinutes())
                .build();

        return ActuatorDtos.ActuatorResponse.from(actuatorRepository.save(actuator));
    }

    @Transactional
    public ActuatorDtos.ActuatorResponse update(String gardenId, String actuatorId,
                                                ActuatorDtos.UpdateActuatorRequest request) {
        Actuator actuator = require(gardenId, actuatorId);
        if (StringUtils.hasText(request.name())) {
            actuator.setName(request.name());
        }
        if (request.maxRuntimeMinutes() != null) {
            actuator.setMaxRuntimeMinutes(request.maxRuntimeMinutes());
        }
        if (request.cooldownMinutes() != null) {
            actuator.setCooldownMinutes(request.cooldownMinutes());
        }
        if (request.enabled() != null) {
            actuator.setEnabled(request.enabled());
        }
        return ActuatorDtos.ActuatorResponse.from(actuatorRepository.save(actuator));
    }

    @Transactional
    public ActuatorDtos.ActuatorResponse setMode(String gardenId, String actuatorId, ActuatorMode mode) {
        Actuator actuator = require(gardenId, actuatorId);
        actuator.setMode(mode);
        Actuator saved = actuatorRepository.save(actuator);
        realtimeBoundary.pushActuator(gardenId, ActuatorDtos.ActuatorResponse.from(saved));
        return ActuatorDtos.ActuatorResponse.from(saved);
    }

    @Transactional
    public ActuatorDtos.CommandAcceptedResponse commandById(String gardenId, String actuatorId,
                                                            CommandType commandType, Integer durationMinutes,
                                                            TriggerSource source, String sourceRef) {
        return command(require(gardenId, actuatorId), commandType, durationMinutes, source, sourceRef, null);
    }

    /** Drives every actuator of a type — used by rules that target "the fan" rather than a device. */
    @Transactional
    public List<ActuatorDtos.CommandAcceptedResponse> commandByType(String gardenId, ActuatorType type,
                                                                    CommandType commandType, Integer durationMinutes,
                                                                    TriggerSource source, String sourceRef,
                                                                    String reasonDetail) {
        return actuatorRepository.findByGardenIdAndType(gardenId, type).stream()
                .map(actuator -> command(actuator, commandType, durationMinutes, source, sourceRef, reasonDetail))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public ActuatorDtos.CommandAcceptedResponse command(Actuator actuator, CommandType commandType,
                                                        Integer durationMinutes, TriggerSource source,
                                                        String sourceRef) {
        return command(actuator, commandType, durationMinutes, source, sourceRef, null);
    }

    /**
     * @param reasonDetail human copy for the timeline parenthetical, e.g. "nhiệt độ vượt 30°C";
     *                     falls back to a generic phrase for the source when null
     * @return the accepted command, or {@code null} when an automated source was refused
     *         (device disabled, MANUAL lock, cooldown, already in the target state).
     *         A USER command surfaces the refusal as a {@link BusinessRuleException} instead,
     *         because a person pressing a button deserves to be told why nothing happened.
     */
    @Transactional
    public ActuatorDtos.CommandAcceptedResponse command(Actuator actuator, CommandType commandType,
                                                        Integer durationMinutes, TriggerSource source,
                                                        String sourceRef, String reasonDetail) {
        Instant now = Instant.now();
        boolean automated = source != TriggerSource.USER;

        if (!actuator.isEnabled()) {
            return refuse(automated, "ACTUATOR_DISABLED", actuator.getName() + " is disabled");
        }
        if (automated && actuator.getMode() == ActuatorMode.MANUAL) {
            return refuse(true, "ACTUATOR_MANUAL", actuator.getName() + " is locked to manual control");
        }
        if (actuator.getState() == commandType.resultingState()) {
            return refuse(automated, "ALREADY_IN_STATE",
                    actuator.getName() + " is already " + commandType.resultingState().getLabel());
        }
        // Cooldown protects the motor; only automated sources are held back by it.
        if (automated && commandType == CommandType.TURN_ON && actuator.isInCooldown(now)) {
            return refuse(true, "ACTUATOR_COOLDOWN", actuator.getName() + " is still cooling down");
        }

        DeviceCommand command = commandRepository.save(DeviceCommand.builder()
                .gardenId(actuator.getGardenId())
                .actuatorId(actuator.getId())
                .deviceCode(actuator.getDeviceCode())
                .channel(actuator.getChannel())
                .command(commandType)
                .durationMinutes(durationMinutes)
                .correlationId(UUID.randomUUID().toString())
                .status(CommandStatus.PENDING)
                .issuedBy(source)
                .issuedByRef(sourceRef)
                .issuedAt(now)
                .build());

        boolean published = commandPublisher.publishCommand(
                actuator.getGardenId(),
                actuator.getDeviceCode(),
                new DeviceDtos.CommandPayload(
                        command.getCorrelationId(),
                        actuator.getChannel(),
                        commandType.name(),
                        durationMinutes,
                        now));

        if (published) {
            command.setStatus(CommandStatus.SENT);
            command.setSentAt(now);
        } else {
            command.setStatus(CommandStatus.FAILED);
            command.setErrorMessage("Broker unavailable");
        }
        commandRepository.save(command);

        // Optimistic local state: the UI must not wait a network round trip to react.
        applyState(actuator, commandType, durationMinutes, source, now);

        eventControl.record(com.greeniot.greensense.entity.AutomationEvent.builder()
                .gardenId(actuator.getGardenId())
                .occurredAt(now)
                .source(source)
                .category(EventCategory.ACTUATOR_CHANGE)
                .title(actuator.getName() + " " + verb(commandType, source))
                .detail(StringUtils.hasText(reasonDetail) ? reasonDetail : detailFor(source))
                .tone(commandType.resultingState() == com.greeniot.greensense.entity.enums.ActuatorState.OFF
                        ? EventTone.GRAY : EventTone.GREEN)
                .actuatorId(actuator.getId())
                .ruleId(source == TriggerSource.RULE ? sourceRef : null)
                .scheduleId(source == TriggerSource.SCHEDULE ? sourceRef : null)
                .build());

        return new ActuatorDtos.CommandAcceptedResponse(
                command.getId(),
                command.getCorrelationId(),
                command.getStatus().name(),
                ActuatorDtos.ActuatorResponse.from(actuator));
    }

    /** Reconciles local state with what the device reports in its ack. */
    @Transactional
    public void applyAck(String correlationId, boolean success, String reportedState, String error) {
        DeviceCommand command = commandRepository.findByCorrelationId(correlationId).orElse(null);
        if (command == null) {
            log.debug("Ack for unknown correlationId {}", correlationId);
            return;
        }

        Instant now = Instant.now();
        command.setStatus(success ? CommandStatus.ACKED : CommandStatus.FAILED);
        command.setAckedAt(now);
        command.setErrorMessage(error);
        commandRepository.save(command);

        if (success || !StringUtils.hasText(reportedState)) {
            return;
        }

        // The device disagreed with our optimistic guess — trust the device.
        actuatorRepository.findById(command.getActuatorId()).ifPresent(actuator -> {
            try {
                actuator.setState(com.greeniot.greensense.entity.enums.ActuatorState.valueOf(reportedState));
                actuator.setLastChangedAt(now);
                Actuator saved = actuatorRepository.save(actuator);
                realtimeBoundary.pushActuator(saved.getGardenId(), ActuatorDtos.ActuatorResponse.from(saved));
            } catch (IllegalArgumentException ex) {
                log.warn("Device reported unknown state '{}' for actuator {}", reportedState, actuator.getId());
            }
        });
    }

    /** Safety net: forces off anything that has run past its auto-off deadline. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void enforceAutoOff() {
        Instant now = Instant.now();
        for (Actuator actuator : actuatorRepository.findByAutoOffAtBefore(now)) {
            if (!actuator.isActive()) {
                actuator.setAutoOffAt(null);
                actuatorRepository.save(actuator);
                continue;
            }
            CommandType off = actuator.getType() == ActuatorType.CURTAIN
                    ? CommandType.CLOSE : CommandType.TURN_OFF;
            log.info("Auto-off deadline reached for actuator {} ({})", actuator.getId(), actuator.getName());
            command(actuator, off, null, TriggerSource.SYSTEM, "auto-off");
        }
    }

    @Transactional(readOnly = true)
    public Actuator require(String gardenId, String actuatorId) {
        return actuatorRepository.findByIdAndGardenId(actuatorId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("Actuator", actuatorId));
    }

    private void applyState(Actuator actuator, CommandType commandType, Integer durationMinutes,
                            TriggerSource source, Instant now) {
        actuator.setState(commandType.resultingState());
        actuator.setLastChangedAt(now);
        actuator.setLastChangedBy(source);

        boolean activating = commandType == CommandType.TURN_ON || commandType == CommandType.OPEN;
        Integer cap = actuator.getMaxRuntimeMinutes();
        Integer effective = durationMinutes != null ? durationMinutes : cap;
        actuator.setAutoOffAt(activating && effective != null
                ? now.plus(Duration.ofMinutes(Math.min(effective, cap == null ? effective : cap)))
                : null);

        Actuator saved = actuatorRepository.save(actuator);
        realtimeBoundary.pushActuator(saved.getGardenId(), ActuatorDtos.ActuatorResponse.from(saved));
    }

    private ActuatorDtos.CommandAcceptedResponse refuse(boolean silent, String code, String message) {
        if (silent) {
            log.debug("Command refused [{}]: {}", code, message);
            return null;
        }
        throw new BusinessRuleException(code, message);
    }

    private static String verb(CommandType commandType, TriggerSource source) {
        String auto = source == TriggerSource.USER ? "" : "tự động ";
        return switch (commandType) {
            case TURN_ON -> auto + "bật";
            case TURN_OFF -> auto + "tắt";
            case OPEN -> auto + "mở";
            case CLOSE -> auto + "đóng";
        };
    }

    private static String detailFor(TriggerSource source) {
        return switch (source) {
            case RULE -> "kích hoạt bởi quy tắc tự động";
            case SCHEDULE -> "theo lịch tưới";
            case SYSTEM -> "an toàn hệ thống";
            case USER -> "điều khiển thủ công";
            case DEVICE -> "báo cáo từ thiết bị";
        };
    }
}
