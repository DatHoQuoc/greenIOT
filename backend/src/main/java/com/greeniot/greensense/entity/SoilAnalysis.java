package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.MeasurementSource;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** ENTITY — a pH snapshot with the fertiliser advice derived from it. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "soil_analyses")
@CompoundIndex(name = "ix_soil_latest", def = "{'gardenId':1,'measuredAt':-1}")
public class SoilAnalysis extends BaseDocument {

    @Indexed
    private String gardenId;

    private String sensorId;

    private Instant measuredAt;

    private Double ph;

    private MeasurementSource source;

    private SoilPhZone zone;

    /** Vietnamese label shown on the badge, e.g. "Đất chua nhẹ". */
    private String zoneLabel;

    private Recommendation recommendation;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Recommendation {
        /** e.g. "Phân NPK 16-16-8 + Vôi bột". */
        private String title;
        private String rationale;
        /** e.g. "200g vôi/m² + 50g NPK/m²". */
        private String dosage;
        /** e.g. "1 lần/tháng". */
        private String frequency;
        @Builder.Default
        private List<String> alternatives = new ArrayList<>();
    }
}
