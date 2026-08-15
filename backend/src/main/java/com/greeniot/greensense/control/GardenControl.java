package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.ActuatorDtos;
import com.greeniot.greensense.boundary.dto.EventDtos;
import com.greeniot.greensense.boundary.dto.GardenDtos;
import com.greeniot.greensense.boundary.dto.SensorDtos;
import com.greeniot.greensense.boundary.dto.SoilDtos;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.PlantProfile;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.GardenType;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.AlertRepository;
import com.greeniot.greensense.repository.AutomationEventRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.PlantProfileRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.repository.SoilAnalysisRepository;
import com.greeniot.greensense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * CONTROL — garden lifecycle, membership and the composed dashboard payload.
 *
 * <p>Two gates, and picking the wrong one is a security bug:
 * {@link #requireAccess(String, String)} for reads and day-to-day operation (owner or
 * household member), {@link #requireOwner(String, String)} for anything that changes what
 * the garden <i>is</i> — hardware registry, rules, schedules, thresholds, membership,
 * deletion.
 */
@Service
@RequiredArgsConstructor
public class GardenControl {

    private final GardenRepository gardenRepository;
    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final AlertRepository alertRepository;
    private final SoilAnalysisRepository soilAnalysisRepository;
    private final AutomationEventRepository automationEventRepository;
    private final PlantProfileRepository plantProfileRepository;
    private final UserRepository userRepository;

    /**
     * Resolves a garden the caller may <b>read or operate</b> — owner or household member.
     *
     * @throws ResourceNotFoundException when the id is unknown <i>or</i> not shared with
     *         the caller — deliberately indistinguishable, so ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public Garden requireAccess(String gardenId, String userId) {
        return gardenRepository.findAccessibleById(gardenId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Garden", gardenId));
    }

    /**
     * Resolves a garden the caller <b>owns</b>. Required for anything that changes the
     * garden's configuration: hardware registry, rules, schedules, thresholds, membership,
     * deletion. A member operates the plot; they do not redefine it.
     */
    @Transactional(readOnly = true)
    public Garden requireOwner(String gardenId, String userId) {
        Garden garden = requireAccess(gardenId, userId);
        if (!garden.isOwnedBy(userId)) {
            throw new AccessDeniedException("Only the garden owner can perform this action");
        }
        return garden;
    }

    @Transactional(readOnly = true)
    public List<GardenDtos.GardenResponse> listForUser(String userId) {
        return gardenRepository.findAllAccessibleBy(userId).stream()
                .map(garden -> GardenDtos.GardenResponse.from(garden, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public GardenDtos.GardenResponse get(String gardenId, String userId) {
        return GardenDtos.GardenResponse.from(requireAccess(gardenId, userId), userId);
    }

    // ── Membership ──────────────────────────────────────────────────────────────

    @Transactional
    public GardenDtos.GardenResponse addMember(String gardenId, String ownerId, String email) {
        Garden garden = requireOwner(gardenId, ownerId);

        User invitee = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No GreenSense account uses " + email + ". Ask them to sign up first."));

        if (invitee.getId().equals(garden.getOwnerId())) {
            throw new BusinessRuleException("ALREADY_OWNER", "That person already owns this garden");
        }
        if (garden.hasMember(invitee.getId())) {
            throw new BusinessRuleException("ALREADY_MEMBER", "That person is already a member");
        }

        if (garden.getMembers() == null) {
            garden.setMembers(new ArrayList<>());
        }
        garden.getMembers().add(Garden.Member.builder()
                .userId(invitee.getId())
                .email(invitee.getEmail())
                .fullName(invitee.getFullName())
                .addedAt(Instant.now())
                .build());

        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), ownerId);
    }

    @Transactional
    public GardenDtos.GardenResponse removeMember(String gardenId, String ownerId, String memberUserId) {
        Garden garden = requireOwner(gardenId, ownerId);
        if (garden.getMembers() != null) {
            garden.getMembers().removeIf(member -> memberUserId.equals(member.getUserId()));
        }
        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), ownerId);
    }

    @Transactional
    public GardenDtos.GardenResponse create(GardenDtos.CreateGardenRequest request, String userId) {
        Garden garden = Garden.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .type(request.type() == null ? GardenType.OUTDOOR : request.type())
                .areaSqm(request.areaSqm())
                .timezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Ho_Chi_Minh")
                .location(request.location() == null ? null : request.location().toEntity())
                .plantProfileId(request.plantProfileId())
                .systemEnabled(true)
                .thresholds(seedThresholds(request.plantProfileId()))
                .build();

        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), userId);
    }

    @Transactional
    public GardenDtos.GardenResponse update(String gardenId, String userId, GardenDtos.UpdateGardenRequest request) {
        Garden garden = requireOwner(gardenId, userId);

        if (StringUtils.hasText(request.name())) {
            garden.setName(request.name());
        }
        if (request.description() != null) {
            garden.setDescription(request.description());
        }
        if (request.type() != null) {
            garden.setType(request.type());
        }
        if (request.areaSqm() != null) {
            garden.setAreaSqm(request.areaSqm());
        }
        if (StringUtils.hasText(request.timezone())) {
            garden.setTimezone(request.timezone());
        }
        if (request.location() != null) {
            garden.setLocation(request.location().toEntity());
        }
        if (request.plantProfileId() != null) {
            garden.setPlantProfileId(request.plantProfileId());
        }
        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), userId);
    }

    @Transactional
    public void delete(String gardenId, String userId) {
        gardenRepository.delete(requireOwner(gardenId, userId));
    }

    /**
     * The hero toggle. Suppresses rules and schedules without unregistering anything.
     *
     * <p>Deliberately open to members, not owner-only: this is the emergency stop. Someone
     * standing in a flooding garden should not have to find the owner to shut it off.
     */
    @Transactional
    public GardenDtos.GardenResponse setSystemEnabled(String gardenId, String userId, boolean enabled) {
        Garden garden = requireAccess(gardenId, userId);
        garden.setSystemEnabled(enabled);
        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), userId);
    }

    @Transactional
    public GardenDtos.GardenResponse updateThresholds(String gardenId, String userId,
                                                      GardenDtos.UpdateThresholdsRequest request) {
        Garden garden = requireOwner(gardenId, userId);
        Map<SensorType, Threshold> thresholds = new EnumMap<>(SensorType.class);

        if (request.thresholds() != null) {
            request.thresholds().forEach((type, dto) -> {
                if (dto != null) {
                    Threshold threshold = dto.toEntity();
                    if (!StringUtils.hasText(threshold.getUnit())) {
                        threshold.setUnit(type.getDefaultUnit());
                    }
                    thresholds.put(type, threshold);
                }
            });
        }
        garden.setThresholds(thresholds);
        return GardenDtos.GardenResponse.from(gardenRepository.save(garden), userId);
    }

    /** One call for the whole home screen. */
    @Transactional(readOnly = true)
    public GardenDtos.DashboardResponse dashboard(String gardenId, String userId) {
        Garden garden = requireAccess(gardenId, userId);

        List<Sensor> sensors = sensorRepository.findByGardenId(gardenId);
        List<SensorDtos.SensorTile> tiles = sensors.stream()
                .map(sensor -> SensorDtos.SensorTile.from(sensor, garden.thresholdFor(sensor.getType())))
                .toList();

        List<ActuatorDtos.ActuatorResponse> actuators = actuatorRepository.findByGardenId(gardenId).stream()
                .map(ActuatorDtos.ActuatorResponse::from)
                .toList();

        SoilDtos.SoilAnalysisResponse latestSoil = soilAnalysisRepository
                .findFirstByGardenIdOrderByMeasuredAtDesc(gardenId)
                .map(SoilDtos.SoilAnalysisResponse::from)
                .orElse(null);

        List<EventDtos.EventResponse> events =
                automationEventRepository.findTop20ByGardenIdOrderByOccurredAtDesc(gardenId).stream()
                        .map(EventDtos.EventResponse::from)
                        .toList();

        // "Live" means at least one sensor is currently reporting.
        boolean live = sensors.stream().anyMatch(sensor -> sensor.getStatus() == SensorStatus.ONLINE);

        return new GardenDtos.DashboardResponse(
                GardenDtos.GardenResponse.from(garden, userId),
                tiles,
                actuators,
                latestSoil,
                alertRepository.countByGardenIdAndReadFalse(gardenId),
                sensors.size(),
                actuatorRepository.countByGardenIdAndType(gardenId, ActuatorType.WATER_PUMP),
                live,
                events);
    }

    /**
     * Copies a plant profile's optimal bands into the new garden, falling back to
     * sensible defaults for common Vietnamese leafy-vegetable plots.
     */
    private Map<SensorType, Threshold> seedThresholds(String plantProfileId) {
        if (StringUtils.hasText(plantProfileId)) {
            PlantProfile profile = plantProfileRepository.findById(plantProfileId).orElse(null);
            if (profile != null && profile.getOptimal() != null && !profile.getOptimal().isEmpty()) {
                return new EnumMap<>(profile.getOptimal());
            }
        }
        return defaultThresholds();
    }

    public static Map<SensorType, Threshold> defaultThresholds() {
        Map<SensorType, Threshold> defaults = new EnumMap<>(SensorType.class);
        defaults.put(SensorType.TEMPERATURE, Threshold.builder()
                .min(15d).max(38d).warnLow(18d).warnHigh(30d).unit("°C").build());
        defaults.put(SensorType.AIR_HUMIDITY, Threshold.builder()
                .min(30d).max(95d).warnLow(45d).warnHigh(85d).unit("%").build());
        defaults.put(SensorType.SOIL_MOISTURE, Threshold.builder()
                .min(20d).max(85d).warnLow(35d).warnHigh(75d).unit("%").build());
        defaults.put(SensorType.LIGHT, Threshold.builder()
                .min(100d).max(60000d).warnLow(400d).warnHigh(20000d).unit("lux").build());
        defaults.put(SensorType.PH, Threshold.builder()
                .min(4.5d).max(9d).warnLow(5.5d).warnHigh(7.5d).unit("pH").build());
        return defaults;
    }
}
