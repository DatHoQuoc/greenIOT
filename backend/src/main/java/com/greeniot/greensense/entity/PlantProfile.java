package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.EnumMap;
import java.util.Map;

/**
 * ENTITY — reference data. Seeds a garden's thresholds when it is created, and backs the
 * "Phù hợp cây họ đậu / Đa số rau củ / Măng tây, bắp cải" zone hints on the soil screen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "plant_profiles")
public class PlantProfile extends BaseDocument {

    private String name;

    private String scientificName;

    private String category;

    @Builder.Default
    private Map<SensorType, Threshold> optimal = new EnumMap<>(SensorType.class);

    private SoilPhZone phZonePreference;

    private String notes;
}
