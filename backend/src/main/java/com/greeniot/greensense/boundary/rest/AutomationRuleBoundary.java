package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.RuleDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.AutomationRuleControl;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BOUNDARY — automation rules ("khi nhiệt độ vượt 30°C thì bật quạt"). */
@RestController
@RequestMapping("/api/v1/gardens/{gardenId}/rules")
@RequiredArgsConstructor
@Tag(name = "Automation rules")
public class AutomationRuleBoundary {

    private final AutomationRuleControl ruleControl;
    private final GardenControl gardenControl;

    @GetMapping
    public ApiResponse<List<RuleDtos.RuleResponse>> list(@PathVariable String gardenId) {
        requireAccess(gardenId);
        return ApiResponse.ok(ruleControl.list(gardenId));
    }

    @PostMapping
    @Operation(summary = "Create a threshold rule")
    public ResponseEntity<ApiResponse<RuleDtos.RuleResponse>> create(
            @PathVariable String gardenId,
            @Valid @RequestBody RuleDtos.SaveRuleRequest request) {

        requireOwner(gardenId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ruleControl.create(gardenId, request)));
    }

    @PutMapping("/{ruleId}")
    public ApiResponse<RuleDtos.RuleResponse> update(
            @PathVariable String gardenId,
            @PathVariable String ruleId,
            @Valid @RequestBody RuleDtos.SaveRuleRequest request) {

        requireOwner(gardenId);
        return ApiResponse.ok(ruleControl.update(gardenId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/enabled")
    public ApiResponse<RuleDtos.RuleResponse> setEnabled(
            @PathVariable String gardenId,
            @PathVariable String ruleId,
            @RequestBody RuleDtos.EnabledRequest request) {

        requireOwner(gardenId);
        return ApiResponse.ok(ruleControl.setEnabled(gardenId, ruleId, request.enabled()));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable String gardenId, @PathVariable String ruleId) {
        requireOwner(gardenId);
        ruleControl.delete(gardenId, ruleId);
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
