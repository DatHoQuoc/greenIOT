package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.enums.GardenType;
import com.greeniot.greensense.entity.enums.SensorType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class GardenDtos {

    private GardenDtos() {
    }

    public record CreateGardenRequest(
            @NotBlank String name,
            String description,
            GardenType type,
            Double areaSqm,
            String timezone,
            String plantProfileId,
            LocationDto location) {
    }

    public record UpdateGardenRequest(
            String name,
            String description,
            GardenType type,
            Double areaSqm,
            String timezone,
            String plantProfileId,
            LocationDto location) {
    }

    public record LocationDto(Double latitude, Double longitude, String address) {

        static LocationDto from(Garden.GeoLocation location) {
            return location == null ? null
                    : new LocationDto(location.getLatitude(), location.getLongitude(), location.getAddress());
        }

        public Garden.GeoLocation toEntity() {
            return Garden.GeoLocation.builder()
                    .latitude(latitude).longitude(longitude).address(address).build();
        }
    }

    public record ThresholdDto(Double min, Double max, Double warnLow, Double warnHigh, String unit) {

        public static ThresholdDto from(Threshold threshold) {
            return threshold == null ? null : new ThresholdDto(
                    threshold.getMin(), threshold.getMax(),
                    threshold.getWarnLow(), threshold.getWarnHigh(), threshold.getUnit());
        }

        public Threshold toEntity() {
            return Threshold.builder()
                    .min(min).max(max).warnLow(warnLow).warnHigh(warnHigh).unit(unit).build();
        }
    }

    public record UpdateThresholdsRequest(Map<SensorType, ThresholdDto> thresholds) {
    }

    public record SystemToggleRequest(boolean enabled) {
    }

    public record AddMemberRequest(@NotBlank @jakarta.validation.constraints.Email String email) {
    }

    public record MemberDto(String userId, String email, String fullName, java.time.Instant addedAt) {

        static MemberDto from(Garden.Member member) {
            return new MemberDto(member.getUserId(), member.getEmail(),
                    member.getFullName(), member.getAddedAt());
        }
    }

    public record GardenResponse(
            String id,
            String name,
            String description,
            GardenType type,
            Double areaSqm,
            String timezone,
            LocationDto location,
            String plantProfileId,
            boolean systemEnabled,
            Map<SensorType, ThresholdDto> thresholds,
            List<MemberDto> members,
            /** Lets the client hide owner-only controls instead of surfacing a 403. */
            boolean viewerIsOwner) {

        public static GardenResponse from(Garden garden, String viewerId) {
            Map<SensorType, ThresholdDto> thresholds = garden.getThresholds() == null ? Map.of()
                    : garden.getThresholds().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, entry -> ThresholdDto.from(entry.getValue())));

            List<MemberDto> members = garden.getMembers() == null ? List.of()
                    : garden.getMembers().stream().map(MemberDto::from).toList();

            return new GardenResponse(
                    garden.getId(),
                    garden.getName(),
                    garden.getDescription(),
                    garden.getType(),
                    garden.getAreaSqm(),
                    garden.getTimezone(),
                    LocationDto.from(garden.getLocation()),
                    garden.getPlantProfileId(),
                    garden.isSystemEnabled(),
                    thresholds,
                    members,
                    garden.isOwnedBy(viewerId));
        }
    }

    /**
     * Everything the home screen needs in one call — sensor tiles, actuator pills,
     * unread badge, latest soil card and the counters in the hero.
     */
    public record DashboardResponse(
            GardenResponse garden,
            List<SensorDtos.SensorTile> sensors,
            List<ActuatorDtos.ActuatorResponse> actuators,
            SoilDtos.SoilAnalysisResponse latestSoil,
            long unreadAlerts,
            long sensorCount,
            long pumpCount,
            boolean live,
            List<EventDtos.EventResponse> recentEvents) {
    }
}
