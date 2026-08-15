package com.greeniot.greensense.bootstrap;

import com.greeniot.greensense.control.GardenControl;
import com.greeniot.greensense.control.IrrigationScheduleControl;
import com.greeniot.greensense.control.SoilAdvisoryControl;
import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.IrrigationSchedule;
import com.greeniot.greensense.entity.PlantProfile;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.SensorReading;
import com.greeniot.greensense.entity.SoilAnalysis;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.DayOfWeekCode;
import com.greeniot.greensense.entity.enums.GardenType;
import com.greeniot.greensense.entity.enums.MeasurementSource;
import com.greeniot.greensense.entity.enums.RuleOperator;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.SoilPhZone;
import com.greeniot.greensense.entity.enums.UserRole;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.AutomationRuleRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.IrrigationScheduleRepository;
import com.greeniot.greensense.repository.PlantProfileRepository;
import com.greeniot.greensense.repository.SensorReadingRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.repository.SoilAnalysisRepository;
import com.greeniot.greensense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Seeds a demo garden that matches the frontend mockup, so the UI has real data to bind to
 * on a fresh database. Disabled in prod via {@code greensense.seed.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "greensense.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final String DEMO_EMAIL = "demo@greensense.vn";
    private static final String DEMO_PASSWORD = "Green@123";
    private static final String NODE = "ESP32-A1";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final GardenRepository gardenRepository;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final ActuatorRepository actuatorRepository;
    private final AutomationRuleRepository ruleRepository;
    private final IrrigationScheduleRepository scheduleRepository;
    private final SoilAnalysisRepository soilAnalysisRepository;
    private final PlantProfileRepository plantProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            log.debug("Demo data already present; skipping seed");
            return;
        }

        User user = userRepository.save(User.builder()
                .email(DEMO_EMAIL)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .fullName("Chủ vườn Demo")
                .role(UserRole.OWNER)
                .enabled(true)
                .build());

        PlantProfile profile = plantProfileRepository.save(PlantProfile.builder()
                .name("Rau ăn lá")
                .category("Rau củ")
                .optimal(GardenControl.defaultThresholds())
                .phZonePreference(SoilPhZone.NEUTRAL)
                .notes("Ưa pH 6.0-7.0, độ ẩm đất 40-70%, tránh nắng gắt buổi trưa.")
                .build());

        Garden garden = gardenRepository.save(Garden.builder()
                .ownerId(user.getId())
                .name("Vườn Nhà")
                .description("Garden Outdoor")
                .type(GardenType.OUTDOOR)
                .areaSqm(24d)
                .timezone(ZONE.getId())
                .plantProfileId(profile.getId())
                .systemEnabled(true)
                .thresholds(GardenControl.defaultThresholds())
                .location(Garden.GeoLocation.builder()
                        .latitude(10.8231).longitude(106.6297).address("TP. Hồ Chí Minh").build())
                .build());

        List<Sensor> sensors = seedSensors(garden.getId());
        seedActuators(garden.getId());
        seedRules(garden.getId());
        seedSchedule(garden.getId());
        seedReadings(garden.getId(), sensors);
        seedSoil(garden.getId(), sensors);

        log.info("Seeded demo garden '{}' — login {} / {}", garden.getName(), DEMO_EMAIL, DEMO_PASSWORD);
    }

    /** 12 probes, matching the hero counter in the mockup. */
    private List<Sensor> seedSensors(String gardenId) {
        List<Sensor> sensors = new ArrayList<>();

        sensors.add(sensor(gardenId, "temp-1", SensorType.TEMPERATURE, "Nhiệt độ khu A", 28.0));
        sensors.add(sensor(gardenId, "temp-2", SensorType.TEMPERATURE, "Nhiệt độ khu B", 27.4));
        sensors.add(sensor(gardenId, "hum-1", SensorType.AIR_HUMIDITY, "Độ ẩm KK khu A", 65.0));
        sensors.add(sensor(gardenId, "hum-2", SensorType.AIR_HUMIDITY, "Độ ẩm KK khu B", 63.5));
        sensors.add(sensor(gardenId, "soil-1", SensorType.SOIL_MOISTURE, "Độ ẩm đất luống 1", 42.0));
        sensors.add(sensor(gardenId, "soil-2", SensorType.SOIL_MOISTURE, "Độ ẩm đất luống 2", 39.0));
        sensors.add(sensor(gardenId, "soil-3", SensorType.SOIL_MOISTURE, "Độ ẩm đất luống 3", 44.5));
        sensors.add(sensor(gardenId, "soil-4", SensorType.SOIL_MOISTURE, "Độ ẩm đất luống 4", 41.2));
        sensors.add(sensor(gardenId, "lux-1", SensorType.LIGHT, "Ánh sáng khu A", 850d));
        sensors.add(sensor(gardenId, "lux-2", SensorType.LIGHT, "Ánh sáng khu B", 780d));
        sensors.add(sensor(gardenId, "ph-1", SensorType.PH, "pH luống 1", 6.2));
        sensors.add(sensor(gardenId, "ph-2", SensorType.PH, "pH luống 2", 6.4));

        return sensorRepository.saveAll(sensors);
    }

    private Sensor sensor(String gardenId, String channel, SensorType type, String name, double lastValue) {
        return Sensor.builder()
                .gardenId(gardenId)
                .deviceCode(NODE)
                .channel(channel)
                .type(type)
                .name(name)
                .unit(type.getDefaultUnit())
                .status(SensorStatus.ONLINE)
                .lastValue(lastValue)
                .lastReadingAt(Instant.now())
                .batteryLevel(87)
                .firmwareVersion("1.2.0")
                .samplingIntervalSec(300)
                .calibration(new Sensor.Calibration(0d, 1d))
                .enabled(true)
                .build();
    }

    /** 2 pumps + a fan + a curtain, matching the automation pills. */
    private void seedActuators(String gardenId) {
        actuatorRepository.saveAll(List.of(
                actuator(gardenId, "pump-1", ActuatorType.WATER_PUMP, "Bơm nước luống 1-2", ActuatorState.ON),
                actuator(gardenId, "pump-2", ActuatorType.WATER_PUMP, "Bơm nước luống 3-4", ActuatorState.OFF),
                actuator(gardenId, "fan-1", ActuatorType.FAN, "Quạt tản nhiệt", ActuatorState.OFF),
                actuator(gardenId, "curtain-1", ActuatorType.CURTAIN, "Rèm che nắng", ActuatorState.CLOSED)));
    }

    private Actuator actuator(String gardenId, String channel, ActuatorType type,
                              String name, ActuatorState state) {
        return Actuator.builder()
                .gardenId(gardenId)
                .deviceCode(NODE)
                .channel(channel)
                .type(type)
                .name(name)
                .state(state)
                .maxRuntimeMinutes(type == ActuatorType.WATER_PUMP ? 20 : 120)
                .cooldownMinutes(type == ActuatorType.WATER_PUMP ? 30 : 5)
                .enabled(true)
                .build();
    }

    /** The rule the mockup's timeline references: fan on when temperature holds above 30 °C. */
    private void seedRules(String gardenId) {
        ruleRepository.saveAll(List.of(
                AutomationRule.builder()
                        .gardenId(gardenId)
                        .name("Quạt tản nhiệt")
                        .enabled(true)
                        .priority(10)
                        .condition(AutomationRule.Condition.builder()
                                .sensorType(SensorType.TEMPERATURE)
                                .operator(RuleOperator.GT)
                                .value(30d)
                                .sustainedForMinutes(5)
                                .build())
                        .action(AutomationRule.Action.builder()
                                .actuatorType(ActuatorType.FAN)
                                .command(CommandType.TURN_ON)
                                .durationMinutes(30)
                                .raiseAlert(false)
                                .build())
                        .cooldownMinutes(15)
                        .build(),

                AutomationRule.builder()
                        .gardenId(gardenId)
                        .name("Tưới khẩn cấp khi đất khô")
                        .enabled(true)
                        .priority(20)
                        .condition(AutomationRule.Condition.builder()
                                .sensorType(SensorType.SOIL_MOISTURE)
                                .operator(RuleOperator.LT)
                                .value(30d)
                                .sustainedForMinutes(15)
                                .build())
                        .action(AutomationRule.Action.builder()
                                .actuatorType(ActuatorType.WATER_PUMP)
                                .command(CommandType.TURN_ON)
                                .durationMinutes(10)
                                .raiseAlert(true)
                                .build())
                        .cooldownMinutes(60)
                        .build(),

                AutomationRule.builder()
                        .gardenId(gardenId)
                        .name("Đóng rèm khi nắng gắt")
                        .enabled(true)
                        .priority(30)
                        .condition(AutomationRule.Condition.builder()
                                .sensorType(SensorType.LIGHT)
                                .operator(RuleOperator.GT)
                                .value(20000d)
                                .sustainedForMinutes(10)
                                .build())
                        .action(AutomationRule.Action.builder()
                                .actuatorType(ActuatorType.CURTAIN)
                                .command(CommandType.CLOSE)
                                .build())
                        .cooldownMinutes(30)
                        .activeFrom(LocalTime.of(9, 0))
                        .activeTo(LocalTime.of(16, 0))
                        .build()));
    }

    private void seedSchedule(String gardenId) {
        Actuator pump = actuatorRepository.findByGardenIdAndType(gardenId, ActuatorType.WATER_PUMP)
                .stream().findFirst().orElse(null);
        if (pump == null) {
            return;
        }

        IrrigationSchedule schedule = IrrigationSchedule.builder()
                .gardenId(gardenId)
                .actuatorId(pump.getId())
                .name("Tưới sáng")
                .enabled(true)
                .daysOfWeek(EnumSet.allOf(DayOfWeekCode.class))
                .startTime(LocalTime.of(6, 0))
                .durationMinutes(15)
                .skipIfSoilMoistureAbove(60d)
                .build();

        schedule.setNextRunAt(IrrigationScheduleControl.computeNextRun(schedule, ZONE, Instant.now()));
        scheduleRepository.save(schedule);
    }

    /** Seven days of plausible history so every chart and range selector has something to draw. */
    private void seedReadings(String gardenId, List<Sensor> sensors) {
        Random random = new Random(42);
        Instant now = Instant.now();
        List<SensorReading> readings = new ArrayList<>();

        for (Sensor sensor : sensors) {
            double base = sensor.getLastValue() == null ? 0 : sensor.getLastValue();
            double amplitude = switch (sensor.getType()) {
                case TEMPERATURE -> 4d;
                case AIR_HUMIDITY -> 8d;
                case SOIL_MOISTURE -> 9d;
                case LIGHT -> 400d;
                case PH -> 0.4d;
            };

            // one sample per hour for 7 days
            for (int hoursAgo = 24 * 7; hoursAgo >= 0; hoursAgo--) {
                double dayCycle = Math.sin((hoursAgo % 24) / 24d * 2 * Math.PI);
                double value = base + dayCycle * amplitude + (random.nextDouble() - 0.5) * amplitude * 0.3;
                value = Math.round(value * 100d) / 100d;

                readings.add(SensorReading.builder()
                        .timestamp(now.minus(Duration.ofHours(hoursAgo)))
                        .meta(SensorReading.Meta.builder()
                                .gardenId(gardenId)
                                .sensorId(sensor.getId())
                                .type(sensor.getType())
                                .build())
                        .value(value)
                        .unit(sensor.getUnit())
                        .build());
            }
        }

        readingRepository.saveAll(readings);
        log.info("Seeded {} sensor readings", readings.size());
    }

    private void seedSoil(String gardenId, List<Sensor> sensors) {
        String phSensorId = sensors.stream()
                .filter(sensor -> sensor.getType() == SensorType.PH)
                .map(Sensor::getId)
                .findFirst()
                .orElse(null);

        SoilPhZone zone = SoilAdvisoryControl.zoneOf(6.2d);
        soilAnalysisRepository.save(SoilAnalysis.builder()
                .gardenId(gardenId)
                .sensorId(phSensorId)
                .measuredAt(Instant.now().minus(Duration.ofHours(2)))
                .ph(6.2d)
                .source(MeasurementSource.SENSOR)
                .zone(zone)
                .zoneLabel(zone.getLabel())
                .recommendation(SoilAdvisoryControl.recommendationFor(zone))
                .build());
    }
}
