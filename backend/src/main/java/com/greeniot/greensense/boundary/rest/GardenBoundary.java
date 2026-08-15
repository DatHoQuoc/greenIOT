package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.EventDtos;
import com.greeniot.greensense.boundary.dto.GardenDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.AutomationEventControl;
import com.greeniot.greensense.control.GardenControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BOUNDARY — garden CRUD, master switch, thresholds, dashboard and timeline. */
@RestController
@RequestMapping("/api/v1/gardens")
@RequiredArgsConstructor
@Tag(name = "Gardens")
public class GardenBoundary {

    private final GardenControl gardenControl;
    private final AutomationEventControl eventControl;

    @GetMapping
    @Operation(summary = "Gardens owned by the caller")
    public ApiResponse<List<GardenDtos.GardenResponse>> list() {
        return ApiResponse.ok(gardenControl.listForUser(SecurityUtils.requireUserId()));
    }

    @PostMapping
    @Operation(summary = "Create a garden, seeded with default thresholds")
    public ResponseEntity<ApiResponse<GardenDtos.GardenResponse>> create(
            @Valid @RequestBody GardenDtos.CreateGardenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(gardenControl.create(request, SecurityUtils.requireUserId())));
    }

    @GetMapping("/{gardenId}")
    public ApiResponse<GardenDtos.GardenResponse> get(@PathVariable String gardenId) {
        return ApiResponse.ok(gardenControl.get(gardenId, SecurityUtils.requireUserId()));
    }

    @PutMapping("/{gardenId}")
    public ApiResponse<GardenDtos.GardenResponse> update(
            @PathVariable String gardenId,
            @Valid @RequestBody GardenDtos.UpdateGardenRequest request) {
        return ApiResponse.ok(gardenControl.update(gardenId, SecurityUtils.requireUserId(), request));
    }

    @DeleteMapping("/{gardenId}")
    public ResponseEntity<Void> delete(@PathVariable String gardenId) {
        gardenControl.delete(gardenId, SecurityUtils.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{gardenId}/dashboard")
    @Operation(summary = "Everything the home screen renders, in one call")
    public ApiResponse<GardenDtos.DashboardResponse> dashboard(@PathVariable String gardenId) {
        return ApiResponse.ok(gardenControl.dashboard(gardenId, SecurityUtils.requireUserId()));
    }

    @PatchMapping("/{gardenId}/system")
    @Operation(summary = "Master on/off switch — suppresses all rules and schedules")
    public ApiResponse<GardenDtos.GardenResponse> toggleSystem(
            @PathVariable String gardenId,
            @RequestBody GardenDtos.SystemToggleRequest request) {
        return ApiResponse.ok(gardenControl.setSystemEnabled(
                gardenId, SecurityUtils.requireUserId(), request.enabled()));
    }

    @PutMapping("/{gardenId}/thresholds")
    @Operation(summary = "Replace the per-metric warning bands")
    public ApiResponse<GardenDtos.GardenResponse> updateThresholds(
            @PathVariable String gardenId,
            @RequestBody GardenDtos.UpdateThresholdsRequest request) {
        return ApiResponse.ok(gardenControl.updateThresholds(
                gardenId, SecurityUtils.requireUserId(), request));
    }

    @PostMapping("/{gardenId}/members")
    @Operation(summary = "Share the garden with an existing GreenSense account")
    public ApiResponse<GardenDtos.GardenResponse> addMember(
            @PathVariable String gardenId,
            @Valid @RequestBody GardenDtos.AddMemberRequest request) {
        return ApiResponse.ok(gardenControl.addMember(
                gardenId, SecurityUtils.requireUserId(), request.email()));
    }

    @DeleteMapping("/{gardenId}/members/{memberUserId}")
    @Operation(summary = "Revoke a member's access")
    public ApiResponse<GardenDtos.GardenResponse> removeMember(
            @PathVariable String gardenId,
            @PathVariable String memberUserId) {
        return ApiResponse.ok(gardenControl.removeMember(
                gardenId, SecurityUtils.requireUserId(), memberUserId));
    }

    @GetMapping("/{gardenId}/events")
    @Operation(summary = "Automation timeline (Lịch sử kích hoạt tự động)")
    public ApiResponse<List<EventDtos.EventResponse>> events(
            @PathVariable String gardenId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sensorId) {

        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
        return ApiResponse.ok(sensorId == null
                ? eventControl.timeline(gardenId, limit)
                : eventControl.timelineForSensor(gardenId, sensorId, limit));
    }
}
