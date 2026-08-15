package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.ExportControl;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.entity.enums.SensorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/** BOUNDARY — the "Xuất dữ liệu" button. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/export")
@RequiredArgsConstructor
@Tag(name = "Export")
public class ExportBoundary {

    private final ExportControl exportControl;
    private final GardenControl gardenControl;

    @GetMapping(value = "/readings.csv", produces = "text/csv")
    @Operation(summary = "Stream readings as CSV (UTF-8 with BOM, Excel-friendly)")
    public void exportReadings(
            @PathVariable String gardenId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletResponse response) throws IOException {

        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());

        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(7)) : from;
        SensorType sensorType = type == null ? null : SensorType.fromSlug(type);

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + exportControl.fileName(sensorType, start, end, "csv") + "\"");

        exportControl.writeCsv(gardenId, sensorType, start, end, response.getOutputStream());
    }

    @GetMapping(value = "/readings.json", produces = "application/json")
    @Operation(summary = "Stream the same window as JSON, for scripts and notebooks")
    public void exportReadingsJson(
            @PathVariable String gardenId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletResponse response) throws IOException {

        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());

        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(7)) : from;
        SensorType sensorType = type == null ? null : SensorType.fromSlug(type);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + exportControl.fileName(sensorType, start, end, "json") + "\"");

        exportControl.writeJson(gardenId, sensorType, start, end, response.getOutputStream());
    }
}
