package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.SoilDtos;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.entity.FertilizerApplication;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.SoilAnalysis;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.MeasurementSource;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.FertilizerApplicationRepository;
import com.greeniot.greensense.repository.SoilAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * CONTROL — turns a pH number into a fertiliser recommendation, and logs applications.
 *
 * <p>The advice table is the domain knowledge the current frontend hardcodes for pH 6.2;
 * deriving it here means every band is covered and the copy lives in one place.
 */
@Service
@RequiredArgsConstructor
public class SoilAdvisoryControl {

    private final SoilAnalysisRepository soilAnalysisRepository;
    private final FertilizerApplicationRepository fertilizerRepository;
    private final AutomationEventControl eventControl;

    /** Static reference for the "Thang đo pH đất" card. */
    public static final List<SoilDtos.PhZoneReference> PH_REFERENCE = List.of(
            new SoilDtos.PhZoneReference(SoilPhZone.STRONGLY_ACIDIC, "Chua nhiều", 0d, 5.5d,
                    "Cần cải tạo trước khi trồng"),
            new SoilDtos.PhZoneReference(SoilPhZone.SLIGHTLY_ACIDIC, "Chua nhẹ", 5.5d, 6.5d,
                    "Phù hợp cây họ đậu, khoai"),
            new SoilDtos.PhZoneReference(SoilPhZone.NEUTRAL, "Trung tính", 6.5d, 7.3d,
                    "Đa số rau củ"),
            new SoilDtos.PhZoneReference(SoilPhZone.ALKALINE, "Kiềm", 7.3d, 14d,
                    "Măng tây, bắp cải"));

    /** Pure function: pH band to zone. */
    public static SoilPhZone zoneOf(double ph) {
        if (ph < 5.5d) {
            return SoilPhZone.STRONGLY_ACIDIC;
        }
        if (ph < 6.5d) {
            return SoilPhZone.SLIGHTLY_ACIDIC;
        }
        if (ph <= 7.3d) {
            return SoilPhZone.NEUTRAL;
        }
        return SoilPhZone.ALKALINE;
    }

    /** Pure function: zone to fertiliser plan. */
    public static SoilAnalysis.Recommendation recommendationFor(SoilPhZone zone) {
        return switch (zone) {
            case STRONGLY_ACIDIC -> SoilAnalysis.Recommendation.builder()
                    .title("Vôi dolomite + Phân hữu cơ")
                    .rationale("Đất chua nhiều khóa lân và làm rễ khó hấp thu dinh dưỡng; "
                            + "cần nâng pH trước khi bón phân hóa học.")
                    .dosage("500g vôi dolomite/m² + 2kg phân hữu cơ/m²")
                    .frequency("1 lần/tháng, đo lại sau 2 tuần")
                    .alternatives(List.of("Vôi bột CaCO₃", "Phân lân nung chảy"))
                    .build();
            case SLIGHTLY_ACIDIC -> SoilAnalysis.Recommendation.builder()
                    .title("Phân NPK 16-16-8 + Vôi bột")
                    .rationale("Đất chua nhẹ cần bổ sung vôi để nâng pH và NPK cân bằng "
                            + "để cung cấp dinh dưỡng cơ bản.")
                    .dosage("200g vôi/m² + 50g NPK/m²")
                    .frequency("1 lần/tháng")
                    .alternatives(List.of("Phân hữu cơ vi sinh", "Phân lân Super"))
                    .build();
            case NEUTRAL -> SoilAnalysis.Recommendation.builder()
                    .title("Phân NPK 20-20-15 duy trì")
                    .rationale("pH đang ở khoảng lý tưởng cho đa số rau củ; "
                            + "chỉ cần duy trì dinh dưỡng, không cần cải tạo.")
                    .dosage("80g NPK/m²")
                    .frequency("1 lần/tháng")
                    .alternatives(List.of("Phân trùn quế", "Phân gà ủ hoai"))
                    .build();
            case ALKALINE -> SoilAnalysis.Recommendation.builder()
                    .title("Lưu huỳnh + Phân hữu cơ hạ pH")
                    .rationale("Đất kiềm làm cây thiếu sắt và kẽm; lưu huỳnh và chất hữu cơ "
                            + "giúp hạ pH về khoảng trung tính.")
                    .dosage("100g lưu huỳnh/m² + 2kg phân hữu cơ/m²")
                    .frequency("1 lần/tháng, đo lại sau 3 tuần")
                    .alternatives(List.of("Phân đạm sunfat (SA)", "Bã cà phê ủ"))
                    .build();
        };
    }

    /** Persists an analysis and records it on the timeline. */
    @Transactional
    public SoilDtos.SoilAnalysisResponse analyse(String gardenId, String sensorId, double ph,
                                                 Instant measuredAt, MeasurementSource source) {
        SoilPhZone zone = zoneOf(ph);

        SoilAnalysis analysis = soilAnalysisRepository.save(SoilAnalysis.builder()
                .gardenId(gardenId)
                .sensorId(sensorId)
                .measuredAt(measuredAt == null ? Instant.now() : measuredAt)
                .ph(ph)
                .source(source)
                .zone(zone)
                .zoneLabel(zone.getLabel())
                .recommendation(recommendationFor(zone))
                .build());

        eventControl.record(
                gardenId,
                source == MeasurementSource.MANUAL ? TriggerSource.USER : TriggerSource.DEVICE,
                EventCategory.CHECK,
                source == MeasurementSource.MANUAL ? "Nhập pH thủ công" : "Kiểm tra pH định kỳ",
                "%.1f pH — %s".formatted(ph, zone.getLabel()),
                zone == SoilPhZone.NEUTRAL ? EventTone.GREEN : EventTone.TEAL);

        return SoilDtos.SoilAnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public SoilDtos.SoilAnalysisResponse latest(String gardenId) {
        return soilAnalysisRepository.findFirstByGardenIdOrderByMeasuredAtDesc(gardenId)
                .map(SoilDtos.SoilAnalysisResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SoilDtos.SoilAnalysisResponse> history(String gardenId, Instant from, Instant to) {
        return soilAnalysisRepository
                .findByGardenIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(gardenId, from, to).stream()
                .map(SoilDtos.SoilAnalysisResponse::from)
                .toList();
    }

    /** "Đánh dấu đã bón phân hôm nay" — idempotent per calendar day. */
    @Transactional
    public SoilDtos.FertilizerApplicationResponse markFertilizerApplied(
            Garden garden, String userId, SoilDtos.MarkFertilizerRequest request) {

        LocalDate today = LocalDate.now(zoneOf(garden));
        SoilAnalysis latest = soilAnalysisRepository
                .findFirstByGardenIdOrderByMeasuredAtDesc(garden.getId()).orElse(null);

        String name = StringUtils.hasText(request == null ? null : request.fertilizerName())
                ? request.fertilizerName()
                : latest != null && latest.getRecommendation() != null
                        ? latest.getRecommendation().getTitle() : "Phân bón";
        String dosage = StringUtils.hasText(request == null ? null : request.dosage())
                ? request.dosage()
                : latest != null && latest.getRecommendation() != null
                        ? latest.getRecommendation().getDosage() : null;

        FertilizerApplication application = FertilizerApplication.builder()
                .gardenId(garden.getId())
                .userId(userId)
                .appliedOn(today)
                .fertilizerName(name)
                .dosage(dosage)
                .note(request == null ? null : request.note())
                .soilAnalysisId(latest == null ? null : latest.getId())
                .build();

        try {
            application = fertilizerRepository.save(application);
        } catch (DuplicateKeyException ex) {
            throw new BusinessRuleException("ALREADY_FERTILIZED", "Hôm nay đã được đánh dấu bón phân");
        }

        eventControl.record(
                garden.getId(),
                TriggerSource.USER,
                EventCategory.FERTILIZER,
                "Đã bón phân",
                dosage == null ? name : name + " — " + dosage,
                EventTone.TEAL);

        return SoilDtos.FertilizerApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public SoilDtos.TodayFertilizerResponse todayFertilizer(Garden garden) {
        LocalDate today = LocalDate.now(zoneOf(garden));
        return fertilizerRepository.findByGardenIdAndAppliedOn(garden.getId(), today)
                .map(app -> new SoilDtos.TodayFertilizerResponse(
                        true, SoilDtos.FertilizerApplicationResponse.from(app)))
                .orElse(new SoilDtos.TodayFertilizerResponse(false, null));
    }

    @Transactional
    public void unmarkFertilizerToday(Garden garden) {
        LocalDate today = LocalDate.now(zoneOf(garden));
        fertilizerRepository.findByGardenIdAndAppliedOn(garden.getId(), today)
                .ifPresent(fertilizerRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<SoilDtos.FertilizerApplicationResponse> fertilizerHistory(String gardenId) {
        return fertilizerRepository.findTop30ByGardenIdOrderByAppliedOnDesc(gardenId).stream()
                .map(SoilDtos.FertilizerApplicationResponse::from)
                .toList();
    }

    private static ZoneId zoneOf(Garden garden) {
        try {
            return ZoneId.of(garden.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
