package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.GardenDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.PlantProfile;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import com.greeniot.greensense.repository.PlantProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BOUNDARY — reference crop profiles.
 *
 * <p>Exists so a client can actually discover the {@code plantProfileId} that
 * {@code POST /gardens} accepts; before this the ids were only reachable by reading the
 * database. Read-only: profiles are curated reference data, not user content.
 */
@RestController
@RequestMapping("/api/v1/plant-profiles")
@RequiredArgsConstructor
@Tag(name = "Plant profiles")
public class PlantProfileBoundary {

    private final PlantProfileRepository plantProfileRepository;

    public record PlantProfileResponse(
            String id,
            String name,
            String scientificName,
            String category,
            SoilPhZone phZonePreference,
            String notes,
            Map<SensorType, GardenDtos.ThresholdDto> optimal) {

        static PlantProfileResponse from(PlantProfile profile) {
            Map<SensorType, GardenDtos.ThresholdDto> optimal = profile.getOptimal() == null ? Map.of()
                    : profile.getOptimal().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> GardenDtos.ThresholdDto.from(entry.getValue())));

            return new PlantProfileResponse(
                    profile.getId(),
                    profile.getName(),
                    profile.getScientificName(),
                    profile.getCategory(),
                    profile.getPhZonePreference(),
                    profile.getNotes(),
                    optimal);
        }
    }

    @GetMapping
    @Operation(summary = "Crop profiles that can seed a new garden's thresholds")
    public ApiResponse<List<PlantProfileResponse>> list() {
        return ApiResponse.ok(plantProfileRepository.findAll().stream()
                .map(PlantProfileResponse::from)
                .toList());
    }

    @GetMapping("/{profileId}")
    public ApiResponse<PlantProfileResponse> get(@PathVariable String profileId) {
        return ApiResponse.ok(plantProfileRepository.findById(profileId)
                .map(PlantProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("PlantProfile", profileId)));
    }
}
