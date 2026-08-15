package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.SoilDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.SoilAdvisoryControl;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.enums.MeasurementSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** BOUNDARY — the soil-analysis screen: pH, fertiliser advice and the "đã bón phân" mark. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/soil")
@RequiredArgsConstructor
@Tag(name = "Soil")
public class SoilBoundary {

    private final SoilAdvisoryControl soilControl;
    private final GardenControl gardenControl;

    @GetMapping("/latest")
    @Operation(summary = "Most recent pH reading with its recommendation")
    public ApiResponse<SoilDtos.SoilAnalysisResponse> latest(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(soilControl.latest(gardenId));
    }

    @GetMapping("/history")
    public ApiResponse<List<SoilDtos.SoilAnalysisResponse>> history(
            @PathVariable String gardenId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        requireAccess(gardenId);
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        return ApiResponse.ok(soilControl.history(gardenId, start, end));
    }

    @PostMapping("/analyze")
    @Operation(summary = "Record a manually measured pH and get the recommendation")
    public ResponseEntity<ApiResponse<SoilDtos.SoilAnalysisResponse>> analyze(
            @PathVariable String gardenId,
            @Valid @RequestBody SoilDtos.ManualPhRequest request) {

        requireAccess(gardenId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                soilControl.analyse(gardenId, null, request.ph(), request.measuredAt(),
                        MeasurementSource.MANUAL)));
    }

    @GetMapping("/ph-scale")
    @Operation(summary = "Reference bands for the 'Thang đo pH đất' card")
    public ApiResponse<List<SoilDtos.PhZoneReference>> phScale(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(SoilAdvisoryControl.PH_REFERENCE);
    }

    @PostMapping("/fertilizer")
    @Operation(summary = "Đánh dấu đã bón phân hôm nay — idempotent per day")
    public ResponseEntity<ApiResponse<SoilDtos.FertilizerApplicationResponse>> markFertilizer(
            @PathVariable String gardenId,
            @RequestBody(required = false) SoilDtos.MarkFertilizerRequest request) {

        String userId = SecurityUtils.requireUserId();
        Garden garden = gardenControl.requireAccess(gardenId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(soilControl.markFertilizerApplied(garden, userId, request)));
    }

    @GetMapping("/fertilizer/today")
    public ApiResponse<SoilDtos.TodayFertilizerResponse> today(@PathVariable String gardenId) {
        return ApiResponse.ok(soilControl.todayFertilizer(requireAccess(gardenId)));
    }

    @DeleteMapping("/fertilizer/today")
    @Operation(summary = "Undo today's mark")
    public ResponseEntity<Void> undoToday(@PathVariable String gardenId) {
        soilControl.unmarkFertilizerToday(requireAccess(gardenId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fertilizer/history")
    public ApiResponse<List<SoilDtos.FertilizerApplicationResponse>> fertilizerHistory(
            @PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(soilControl.fertilizerHistory(gardenId));
    }

    private Garden requireAccess(String gardenId) {
        return gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
