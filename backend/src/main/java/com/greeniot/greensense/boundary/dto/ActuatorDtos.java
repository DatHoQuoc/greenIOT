package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.enums.ActuatorMode;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.TriggerSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ActuatorDtos {

    private ActuatorDtos() {
    }

    public record RegisterActuatorRequest(
            @NotBlank String deviceCode,
            @NotBlank String channel,
            @NotNull ActuatorType type,
            String name,
            Integer maxRuntimeMinutes,
            Integer cooldownMinutes) {
    }

    public record UpdateActuatorRequest(
            String name,
            Integer maxRuntimeMinutes,
            Integer cooldownMinutes,
            Boolean enabled) {
    }

    public record CommandRequest(
            @NotNull CommandType command,
            @Min(1) Integer durationMinutes) {
    }

    public record ModeRequest(@NotNull ActuatorMode mode) {
    }

    public record ActuatorResponse(
            String id,
            String gardenId,
            String deviceCode,
            String channel,
            ActuatorType type,
            String typeLabel,
            String name,
            ActuatorState state,
            String stateLabel,
            ActuatorMode mode,
            Instant lastChangedAt,
            TriggerSource lastChangedBy,
            Instant autoOffAt,
            boolean enabled) {

        public static ActuatorResponse from(Actuator actuator) {
            return new ActuatorResponse(
                    actuator.getId(),
                    actuator.getGardenId(),
                    actuator.getDeviceCode(),
                    actuator.getChannel(),
                    actuator.getType(),
                    actuator.getType().getLabel(),
                    actuator.getName(),
                    actuator.getState(),
                    actuator.getState().getLabel(),
                    actuator.getMode(),
                    actuator.getLastChangedAt(),
                    actuator.getLastChangedBy(),
                    actuator.getAutoOffAt(),
                    actuator.isEnabled());
        }
    }

    /** Returned after a command so the client knows whether the device has acked yet. */
    public record CommandAcceptedResponse(
            String commandId,
            String correlationId,
            String status,
            ActuatorResponse actuator) {
    }
}
