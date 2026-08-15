package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.ActuatorDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.ActuatorControl;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.entity.enums.TriggerSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BOUNDARY — pump / curtain / fan control. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/actuators")
@RequiredArgsConstructor
@Tag(name = "Actuators")
public class ActuatorBoundary {

    private final ActuatorControl actuatorControl;
    private final GardenControl gardenControl;

    @GetMapping
    public ApiResponse<List<ActuatorDtos.ActuatorResponse>> list(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(actuatorControl.list(gardenId));
    }

    @PostMapping
    @Operation(summary = "Register a controllable device on a channel")
    public ResponseEntity<ApiResponse<ActuatorDtos.ActuatorResponse>> register(
            @PathVariable String gardenId,
            @Valid @RequestBody ActuatorDtos.RegisterActuatorRequest request) {

        requireOwner(gardenId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actuatorControl.register(gardenId, request)));
    }

    @PutMapping("/{actuatorId}")
    public ApiResponse<ActuatorDtos.ActuatorResponse> update(
            @PathVariable String gardenId,
            @PathVariable String actuatorId,
            @Valid @RequestBody ActuatorDtos.UpdateActuatorRequest request) {

        requireOwner(gardenId);
        return ApiResponse.ok(actuatorControl.update(gardenId, actuatorId, request));
    }

    @PostMapping("/{actuatorId}/command")
    @Operation(summary = "Manually drive the device; refusals are returned as 409 with a reason")
    public ApiResponse<ActuatorDtos.CommandAcceptedResponse> command(
            @PathVariable String gardenId,
            @PathVariable String actuatorId,
            @Valid @RequestBody ActuatorDtos.CommandRequest request) {

        String userId = SecurityUtils.requireUserId();
        gardenControl.requireAccess(gardenId, userId);
        return ApiResponse.ok(actuatorControl.commandById(
                gardenId, actuatorId, request.command(), request.durationMinutes(),
                TriggerSource.USER, userId));
    }

    @PatchMapping("/{actuatorId}/mode")
    @Operation(summary = "AUTO lets rules drive the device; MANUAL locks it to user commands")
    public ApiResponse<ActuatorDtos.ActuatorResponse> setMode(
            @PathVariable String gardenId,
            @PathVariable String actuatorId,
            @Valid @RequestBody ActuatorDtos.ModeRequest request) {

        requireAccess(gardenId);
        return ApiResponse.ok(actuatorControl.setMode(gardenId, actuatorId, request.mode()));
    }

    /** Config changes are owner-only; members operate the garden, they do not redefine it. */
    private void requireOwner(String gardenId) {
        gardenControl.requireOwner(gardenId, SecurityUtils.requireUserId());
    }

    private void requireAccess(String gardenId) {
        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
