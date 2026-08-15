package com.greeniot.greensense;

import com.greeniot.greensense.control.ActuatorControl;
import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.RuleEngineControl;
import com.greeniot.greensense.control.SoilAdvisoryControl;
import com.greeniot.greensense.control.TelemetryIngestControl;
import com.greeniot.greensense.repository.AlertRepository;
import com.greeniot.greensense.repository.AutomationRuleRepository;
import com.greeniot.greensense.repository.SensorReadingRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against an embedded MongoDB.
 *
 * <p>This is the test that catches wiring mistakes the compiler cannot: circular bean
 * dependencies between controls, and Spring Data derived query methods whose names do not
 * resolve against the entity metadata (those only fail when the repository proxy is built).
 */
@IntegrationTest
class ApplicationContextSmokeTest {

    @Autowired
    private GardenControl gardenControl;

    @Autowired
    private TelemetryIngestControl telemetryIngestControl;

    @Autowired
    private RuleEngineControl ruleEngineControl;

    @Autowired
    private ActuatorControl actuatorControl;

    @Autowired
    private SoilAdvisoryControl soilAdvisoryControl;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private AutomationRuleRepository automationRuleRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void contextLoadsWithEveryControlAndRepository() {
        assertThat(gardenControl).isNotNull();
        assertThat(telemetryIngestControl).isNotNull();
        assertThat(ruleEngineControl).isNotNull();
        assertThat(actuatorControl).isNotNull();
        assertThat(soilAdvisoryControl).isNotNull();
    }

    /** Exercises the derived queries so their method names are validated, not just parsed. */
    @Test
    void derivedQueriesResolve() {
        assertThat(sensorRepository.findByGardenId("none")).isEmpty();
        assertThat(sensorRepository.findByEnabledTrueAndLastReadingAtBefore(java.time.Instant.now())).isEmpty();
        assertThat(sensorReadingRepository.findFirstByMetaSensorIdOrderByTimestampDesc("none")).isEmpty();
        assertThat(automationRuleRepository
                .findByGardenIdAndEnabledTrueAndConditionSensorTypeOrderByPriorityAsc(
                        "none", com.greeniot.greensense.entity.enums.SensorType.TEMPERATURE)).isEmpty();
        assertThat(alertRepository.existsByGardenIdAndCodeAndSensorIdAndRaisedAtAfter(
                "none", "X", "sensor-1", java.time.Instant.now())).isFalse();
        assertThat(alertRepository.existsByGardenIdAndCodeAndSensorIdIsNullAndRaisedAtAfter(
                "none", "X", java.time.Instant.now())).isFalse();
        assertThat(alertRepository.countByGardenIdAndReadFalse("none")).isZero();
    }
}
