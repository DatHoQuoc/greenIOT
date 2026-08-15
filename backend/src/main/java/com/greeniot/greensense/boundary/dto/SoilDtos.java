package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.FertilizerApplication;
import com.greeniot.greensense.entity.SoilAnalysis;
import com.greeniot.greensense.entity.enums.MeasurementSource;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SoilDtos {

    private SoilDtos() {
    }

    public record ManualPhRequest(
            @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double ph,
            Instant measuredAt) {
    }

    public record RecommendationDto(
            String title,
            String rationale,
            String dosage,
            String frequency,
            List<String> alternatives) {

        public static RecommendationDto from(SoilAnalysis.Recommendation recommendation) {
            return recommendation == null ? null : new RecommendationDto(
                    recommendation.getTitle(),
                    recommendation.getRationale(),
                    recommendation.getDosage(),
                    recommendation.getFrequency(),
                    recommendation.getAlternatives());
        }
    }

    public record SoilAnalysisResponse(
            String id,
            String gardenId,
            Instant measuredAt,
            Double ph,
            MeasurementSource source,
            SoilPhZone zone,
            String zoneLabel,
            RecommendationDto recommendation) {

        public static SoilAnalysisResponse from(SoilAnalysis analysis) {
            return analysis == null ? null : new SoilAnalysisResponse(
                    analysis.getId(),
                    analysis.getGardenId(),
                    analysis.getMeasuredAt(),
                    analysis.getPh(),
                    analysis.getSource(),
                    analysis.getZone(),
                    analysis.getZoneLabel(),
                    RecommendationDto.from(analysis.getRecommendation()));
        }
    }

    public record MarkFertilizerRequest(String fertilizerName, String dosage, String note) {
    }

    public record FertilizerApplicationResponse(
            String id,
            LocalDate appliedOn,
            String fertilizerName,
            String dosage,
            String note,
            String soilAnalysisId) {

        public static FertilizerApplicationResponse from(FertilizerApplication application) {
            return application == null ? null : new FertilizerApplicationResponse(
                    application.getId(),
                    application.getAppliedOn(),
                    application.getFertilizerName(),
                    application.getDosage(),
                    application.getNote(),
                    application.getSoilAnalysisId());
        }
    }

    /** Answers "đã bón phân hôm nay?" without the client comparing dates itself. */
    public record TodayFertilizerResponse(boolean applied, FertilizerApplicationResponse application) {
    }

    /** Static reference data for the "Thang đo pH đất" card. */
    public record PhZoneReference(SoilPhZone zone, String label, Double from, Double to, String suitableFor) {
    }
}
