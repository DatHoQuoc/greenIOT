package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.GardenType;
import com.greeniot.greensense.entity.enums.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** ENTITY — a physical plot. Root of every garden-scoped aggregate. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "gardens")
public class Garden extends BaseDocument {

    @Indexed
    private String ownerId;

    /** e.g. "Vườn Nhà". */
    private String name;

    /** e.g. "Garden Outdoor". */
    private String description;

    @Builder.Default
    private GardenType type = GardenType.OUTDOOR;

    private Double areaSqm;

    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    private GeoLocation location;

    /** Seeds the default thresholds when the garden is created. */
    private String plantProfileId;

    /**
     * Master switch behind the hero toggle. When false every rule and schedule is
     * suppressed — manual commands still work so the user can recover the garden.
     */
    @Builder.Default
    private boolean systemEnabled = true;

    /** Per-metric override of the plant profile's optimal band. */
    @Builder.Default
    private Map<SensorType, Threshold> thresholds = new EnumMap<>(SensorType.class);

    /**
     * Household members who share the plot.
     *
     * <p>A member can watch the garden and operate it — press the pump, acknowledge an
     * alert, mark fertiliser — but cannot change its configuration or membership. Only the
     * owner registers hardware, edits rules and schedules, or deletes the garden.
     */
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    public Threshold thresholdFor(SensorType type) {
        return thresholds == null ? null : thresholds.get(type);
    }

    public boolean isOwnedBy(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean hasMember(String userId) {
        return members != null && members.stream()
                .anyMatch(member -> member.getUserId() != null && member.getUserId().equals(userId));
    }

    public boolean isAccessibleBy(String userId) {
        return isOwnedBy(userId) || hasMember(userId);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Member {
        private String userId;
        private String email;
        private String fullName;
        private Instant addedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GeoLocation {
        private Double latitude;
        private Double longitude;
        private String address;
    }
}
