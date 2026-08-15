package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.ScheduleDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.IrrigationScheduleControl;
import com.greeniot.greensense.entity.Garden;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BOUNDARY — the "Lịch tưới" tab. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/schedules")
@RequiredArgsConstructor
@Tag(name = "Irrigation schedules")
public class IrrigationScheduleBoundary {

    private final IrrigationScheduleControl scheduleControl;
    private final GardenControl gardenControl;

    @GetMapping
    public ApiResponse<List<ScheduleDtos.ScheduleResponse>> list(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(scheduleControl.list(gardenId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleDtos.ScheduleResponse>> create(
            @PathVariable String gardenId,
            @Valid @RequestBody ScheduleDtos.SaveScheduleRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(scheduleControl.create(requireOwner(gardenId), request)));
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ScheduleDtos.ScheduleResponse> update(
            @PathVariable String gardenId,
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleDtos.SaveScheduleRequest request) {

        return ApiResponse.ok(scheduleControl.update(requireOwner(gardenId), scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable String gardenId, @PathVariable String scheduleId) {
        requireOwner(gardenId);
        scheduleControl.delete(gardenId, scheduleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{scheduleId}/run-now")
    @Operation(summary = "Water immediately — bypasses the master switch and the moisture skip")
    public ApiResponse<ScheduleDtos.ScheduleResponse> runNow(
            @PathVariable String gardenId, @PathVariable String scheduleId) {
        return ApiResponse.ok(scheduleControl.runNow(requireAccess(gardenId), scheduleId));
    }

    /** Schedule definitions are configuration — owner-only. Running one is operation. */
    private Garden requireOwner(String gardenId) {
        return gardenControl.requireOwner(gardenId, SecurityUtils.requireUserId());
    }

    private Garden requireAccess(String gardenId) {
        return gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
