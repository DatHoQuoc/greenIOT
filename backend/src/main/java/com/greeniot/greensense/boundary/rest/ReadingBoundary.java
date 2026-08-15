package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.ReadingDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.ReadingAnalyticsControl;
import com.greeniot.greensense.control.TelemetryIngestControl;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.enums.SensorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** BOUNDARY — measurement history, statistics and the HTTP ingest fallback. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/readings")
@RequiredArgsConstructor
@Tag(name = "Readings")
public class ReadingBoundary {

    private final ReadingAnalyticsControl analyticsControl;
    private final TelemetryIngestControl ingestControl;
    private final GardenControl gardenControl;

    @GetMapping("/latest")
    @Operation(summary = "Newest reading per sensor")
    public ApiResponse<List<ReadingDtos.ReadingResponse>> latest(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(analyticsControl.latestPerSensor(gardenId));
    }

    @GetMapping("/series")
    @Operation(summary = "Down-sampled chart series for one metric")
    public ApiResponse<ReadingDtos.SeriesResponse> series(
            @PathVariable String gardenId,
            @RequestParam String type,
            @RequestParam(defaultValue = "24H") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        requireAccess(gardenId);
        return ApiResponse.ok(analyticsControl.series(gardenId, SensorType.fromSlug(type), range, from, to));
    }

    @GetMapping("/summary")
    @Operation(summary = "Current / min / max / trend for one metric over a named range")
    public ApiResponse<ReadingDtos.SummaryResponse> summary(
            @PathVariable String gardenId,
            @RequestParam String type,
            @RequestParam(defaultValue = "24H") String range) {

        Garden garden = requireAccess(gardenId);
        return ApiResponse.ok(analyticsControl.summary(garden, SensorType.fromSlug(type), range));
    }

    /**
     * HTTP ingest fallback for nodes without an MQTT client. Requires the same JWT as the
     * rest of the API — device-to-cloud traffic normally goes over the broker instead.
     */
    @PostMapping("/ingest")
    @Operation(summary = "Submit one or more readings over HTTP")
    public ResponseEntity<ApiResponse<Integer>> ingest(
            @PathVariable String gardenId,
            @Valid @RequestBody ReadingDtos.IngestBatchRequest request) {

        requireAccess(gardenId);
        int accepted = 0;
        for (ReadingDtos.IngestRequest reading : request.readings()) {
            boolean stored = ingestControl.ingest(
                    gardenId, reading.deviceCode(), reading.channel(),
                    reading.value(), reading.timestamp()).isPresent();
            if (stored) {
                accepted++;
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(accepted));
    }

    private Garden requireAccess(String gardenId) {
        return gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
