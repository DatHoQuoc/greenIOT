package com.greeniot.greensense.control;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.RuleOperator;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.AlertRepository;
import com.greeniot.greensense.repository.AutomationEventRepository;
import com.greeniot.greensense.repository.AutomationRuleRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end exercise of the ingestion path: a reading arrives, breaches the garden
 * threshold, fires an automation rule, drives the fan, and leaves an audit trail.
 */
@IntegrationTest
class TelemetryPipelineTest {

    @Autowired private TelemetryIngestControl ingestControl;
    @Autowired private SoilAdvisoryControl soilControl;
    @Autowired private GardenRepository gardenRepository;
    @Autowired private SensorRepository sensorRepository;
    @Autowired private ActuatorRepository actuatorRepository;
    @Autowired private AutomationRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AutomationEventRepository eventRepository;
    @Autowired private TestFixtures fixtures;

    private Garden garden;
    private Sensor temperatureSensor;
    private Actuator fan;

    @BeforeEach
    void setUp() {
        fixtures.wipe();

        Map<SensorType, Threshold> thresholds = new EnumMap<>(SensorType.class);
        thresholds.put(SensorType.TEMPERATURE, Threshold.builder()
                .min(15d).max(38d).warnLow(18d).warnHigh(30d).unit("°C").build());

        garden = gardenRepository.save(Garden.builder()
                .ownerId("owner-1")
                .name("Vườn Nhà")
                .timezone("Asia/Ho_Chi_Minh")
                .systemEnabled(true)
                .thresholds(thresholds)
                .build());

        temperatureSensor = sensorRepository.save(Sensor.builder()
                .gardenId(garden.getId())
                .deviceCode("ESP32-A1")
                .channel("temp-1")
                .type(SensorType.TEMPERATURE)
                .name("Nhiệt độ khu A")
                .unit("°C")
                .status(SensorStatus.OFFLINE)
                .calibration(new Sensor.Calibration(0d, 1d))
                .enabled(true)
                .build());

        fan = actuatorRepository.save(Actuator.builder()
                .gardenId(garden.getId())
                .deviceCode("ESP32-A1")
                .channel("fan-1")
                .type(ActuatorType.FAN)
                .name("Quạt tản nhiệt")
                .state(ActuatorState.OFF)
                .enabled(true)
                .build());

        ruleRepository.save(AutomationRule.builder()
                .gardenId(garden.getId())
                .name("Quạt tản nhiệt")
                .enabled(true)
                .condition(AutomationRule.Condition.builder()
                        .sensorType(SensorType.TEMPERATURE)
                        .operator(RuleOperator.GT)
                        .value(30d)
                        .sustainedForMinutes(0)
                        .build())
                .action(AutomationRule.Action.builder()
                        .actuatorType(ActuatorType.FAN)
                        .command(CommandType.TURN_ON)
                        .durationMinutes(30)
                        .raiseAlert(false)
                        .alertSeverity(AlertSeverity.WARNING)
                        .build())
                .cooldownMinutes(15)
                .build());
    }

    @Test
    void readingAboveThresholdRaisesAlertAndStartsTheFan() {
        ingestControl.ingest(garden.getId(), "ESP32-A1", "temp-1", 31.5d, null);

        Sensor refreshed = sensorRepository.findById(temperatureSensor.getId()).orElseThrow();
        assertThat(refreshed.getLastValue()).isEqualTo(31.5d);
        assertThat(refreshed.getStatus()).isEqualTo(SensorStatus.ONLINE);

        Actuator refreshedFan = actuatorRepository.findById(fan.getId()).orElseThrow();
        assertThat(refreshedFan.getState()).isEqualTo(ActuatorState.ON);
        assertThat(refreshedFan.getLastChangedBy()).isEqualTo(TriggerSource.RULE);
        assertThat(refreshedFan.getAutoOffAt()).isNotNull();

        assertThat(alertRepository.countByGardenIdAndReadFalse(garden.getId())).isEqualTo(1);

        var events = eventRepository.findTop20ByGardenIdOrderByOccurredAtDesc(garden.getId());
        assertThat(events)
                .anyMatch(event -> event.getCategory() == EventCategory.ACTUATOR_CHANGE
                        && event.getTitle().equals("Quạt tản nhiệt tự động bật")
                        && "nhiệt độ vượt 30°C".equals(event.getDetail()));
    }

    @Test
    void readingWithinThresholdChangesNothing() {
        ingestControl.ingest(garden.getId(), "ESP32-A1", "temp-1", 27d, null);

        assertThat(actuatorRepository.findById(fan.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.OFF);
        assertThat(alertRepository.countByGardenIdAndReadFalse(garden.getId())).isZero();
    }

    @Test
    void masterSwitchOffSuppressesAutomationButStillStoresTheReading() {
        garden.setSystemEnabled(false);
        gardenRepository.save(garden);

        ingestControl.ingest(garden.getId(), "ESP32-A1", "temp-1", 33d, null);

        assertThat(sensorRepository.findById(temperatureSensor.getId()).orElseThrow().getLastValue())
                .isEqualTo(33d);
        assertThat(actuatorRepository.findById(fan.getId()).orElseThrow().getState())
                .isEqualTo(ActuatorState.OFF);
    }

    @Test
    void telemetryFromAnUnregisteredChannelIsDropped() {
        assertThat(ingestControl.ingest(garden.getId(), "ESP32-A1", "ghost-9", 30d, null)).isEmpty();
    }

    @Test
    void phReadingProducesTheFertiliserRecommendation() {
        sensorRepository.save(Sensor.builder()
                .gardenId(garden.getId())
                .deviceCode("ESP32-A1")
                .channel("ph-1")
                .type(SensorType.PH)
                .name("pH luống 1")
                .unit("pH")
                .calibration(new Sensor.Calibration(0d, 1d))
                .enabled(true)
                .build());

        ingestControl.ingest(garden.getId(), "ESP32-A1", "ph-1", 6.2d, null);

        var analysis = soilControl.latest(garden.getId());
        assertThat(analysis).isNotNull();
        assertThat(analysis.zone()).isEqualTo(SoilPhZone.SLIGHTLY_ACIDIC);
        assertThat(analysis.zoneLabel()).isEqualTo("Đất chua nhẹ");
        assertThat(analysis.recommendation().title()).isEqualTo("Phân NPK 16-16-8 + Vôi bột");
        assertThat(analysis.recommendation().dosage()).isEqualTo("200g vôi/m² + 50g NPK/m²");
    }
}
