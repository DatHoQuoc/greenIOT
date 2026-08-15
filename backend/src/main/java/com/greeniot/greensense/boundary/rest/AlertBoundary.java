package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.AlertDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.dto.PageResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.AlertControl;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.entity.enums.AlertStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BOUNDARY — the "Cảnh báo" tab and the bell badge. */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts")
public class AlertBoundary {

    private final AlertControl alertControl;
    private final GardenControl gardenControl;

    @GetMapping
    public ApiResponse<PageResponse<AlertDtos.AlertResponse>> list(
            @PathVariable String gardenId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        requireAccess(gardenId);
        return ApiResponse.ok(alertControl.list(
                gardenId, status, unreadOnly, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Badge count for the notification bell")
    public ApiResponse<AlertDtos.UnreadCountResponse> unreadCount(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(new AlertDtos.UnreadCountResponse(alertControl.unreadCount(gardenId)));
    }

    @PatchMapping("/{alertId}/read")
    public ApiResponse<AlertDtos.AlertResponse> markRead(@PathVariable String gardenId,
                                                         @PathVariable String alertId) {
        requireAccess(gardenId);
        return ApiResponse.ok(alertControl.markRead(gardenId, alertId));
    }

    @PatchMapping("/{alertId}/acknowledge")
    public ApiResponse<AlertDtos.AlertResponse> acknowledge(@PathVariable String gardenId,
                                                            @PathVariable String alertId) {
        requireAccess(gardenId);
        return ApiResponse.ok(alertControl.acknowledge(gardenId, alertId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Long> markAllRead(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(alertControl.markAllRead(gardenId));
    }

    private void requireAccess(String gardenId) {
        gardenControl.requireAccess(gardenId, SecurityUtils.requireUserId());
    }
}
