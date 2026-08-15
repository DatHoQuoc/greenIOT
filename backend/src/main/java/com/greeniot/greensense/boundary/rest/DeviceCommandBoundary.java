package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.DeviceCommandControl;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.entity.DeviceCommand;
import com.greeniot.greensense.entity.enums.CommandStatus;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.TriggerSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * BOUNDARY — outbound command history.
 *
 * <p>This is the answer to "I pressed the pump button, did the hardware actually hear
 * me?". The command lifecycle (PENDING → SENT → ACKED / TIMEOUT / FAILED) was already
 * tracked but had no way out of the database.
 */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/commands")
@RequiredArgsConstructor
@Tag(name = "Device commands")
public class DeviceCommandBoundary {

    private final DeviceCommandControl commandControl;
    private final GardenControl gardenControl;

    public record CommandResponse(
            String id,
            String actuatorId,
            String deviceCode,
            String channel,
            CommandType command,
            Integer durationMinutes,
            CommandStatus status,
            TriggerSource issuedBy,
            Instant issuedAt,
            Instant sentAt,
            Instant ackedAt,
            String errorMessage) {

        static CommandResponse from(DeviceCommand command) {
            return new CommandResponse(
                    command.getId(),
                    command.getActuatorId(),
                    command.getDeviceCode(),
                    command.getChannel(),
                    command.getCommand(),
                    command.getDurationMinutes(),
                    command.getStatus(),
                    command.getIssuedBy(),
                    command.getIssuedAt(),
                    command.getSentAt(),
                    command.getAckedAt(),
                    command.getErrorMessage());
        }
    }

    @GetMapping
    @Operation(summary = "The 20 most recent commands sent to this garden's hardware")
    public ApiResponse<List<CommandResponse>> recent(@PathVariable String gardenId) {
        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
        return ApiResponse.ok(commandControl.recent(gardenId).stream()
                .map(CommandResponse::from)
                .toList());
    }
}
