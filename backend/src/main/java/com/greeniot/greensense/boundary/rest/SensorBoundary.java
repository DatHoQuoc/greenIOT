package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.SensorDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.SensorControl;
import com.greeniot.greensense.entity.enums.SensorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BOUNDARY — sensor registry. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/sensors")
@RequiredArgsConstructor
@Tag(name = "Sensors")
public class SensorBoundary {

    private final SensorControl sensorControl;
    private final GardenControl gardenControl;

    @GetMapping
    @Operation(summary = "Sensors in the garden, optionally filtered by metric")
    public ApiResponse<List<SensorDtos.SensorResponse>> list(
            @PathVariable String gardenId,
            @RequestParam(required = false) String type) {

        requireAccess(gardenId);
        return ApiResponse.ok(sensorControl.list(gardenId, type == null ? null : SensorType.fromSlug(type)));
    }

    @PostMapping
    @Operation(summary = "Register a probe on a device channel")
    public ResponseEntity<ApiResponse<SensorDtos.SensorResponse>> register(
            @PathVariable String gardenId,
            @Valid @RequestBody SensorDtos.RegisterSensorRequest request) {

        requireOwner(gardenId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(sensorControl.register(gardenId, request)));
    }

    @GetMapping("/{sensorId}")
    public ApiResponse<SensorDtos.SensorResponse> get(@PathVariable String gardenId,
                                                      @PathVariable String sensorId) {
        requireAccess(gardenId);
        return ApiResponse.ok(sensorControl.get(gardenId, sensorId));
    }

    @PutMapping("/{sensorId}")
    public ApiResponse<SensorDtos.SensorResponse> update(
            @PathVariable String gardenId,
            @PathVariable String sensorId,
            @Valid @RequestBody SensorDtos.UpdateSensorRequest request) {

        requireOwner(gardenId);
        return ApiResponse.ok(sensorControl.update(gardenId, sensorId, request));
    }

    @DeleteMapping("/{sensorId}")
    public ResponseEntity<Void> delete(@PathVariable String gardenId, @PathVariable String sensorId) {
        requireOwner(gardenId);
        sensorControl.delete(gardenId, sensorId);
        return ResponseEntity.noContent().build();
    }

    /** Config changes are owner-only; members operate the garden, they do not redefine it. */
    private void requireOwner(String gardenId) {
        gardenControl.requireOwner(gardenId, SecurityUtils.requireUserId());
    }

    private void requireAccess(String gardenId) {
        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
