package com.greeniot.greensense.support;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.entity.enums.UserRole;
import com.greeniot.greensense.repository.ActuatorRepository;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.SensorRepository;
import com.greeniot.greensense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** Builders for the objects nearly every test needs. */
@Component
@RequiredArgsConstructor
public class TestFixtures {

    public static final String PASSWORD = "Green@123";

    private final UserRepository userRepository;
    private final GardenRepository gardenRepository;
    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    /** Empties every collection so tests never inherit each other's rows. */
    public void wipe() {
        mongoTemplate.getCollectionNames().stream()
                .filter(name -> !name.startsWith("system."))
                .forEach(name -> mongoTemplate.getCollection(name).deleteMany(new Document()));
    }

    public User user(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + email)
                .role(UserRole.OWNER)
                .enabled(true)
                .build());
    }

    public Garden garden(String ownerId) {
        Map<SensorType, Threshold> thresholds = new EnumMap<>(SensorType.class);
        thresholds.put(SensorType.TEMPERATURE, Threshold.builder()
                .min(15d).max(38d).warnLow(18d).warnHigh(30d).unit("°C").build());
        thresholds.put(SensorType.SOIL_MOISTURE, Threshold.builder()
                .min(20d).max(85d).warnLow(35d).warnHigh(75d).unit("%").build());

        return gardenRepository.save(Garden.builder()
                .ownerId(ownerId)
                .name("Vườn Nhà")
                .timezone("Asia/Ho_Chi_Minh")
                .systemEnabled(true)
                .thresholds(thresholds)
                .build());
    }

    public Sensor sensor(String gardenId, String channel, SensorType type) {
        return sensorRepository.save(Sensor.builder()
                .gardenId(gardenId)
                .deviceCode("ESP32-A1")
                .channel(channel)
                .type(type)
                .name(type.getLabel())
                .unit(type.getDefaultUnit())
                .status(SensorStatus.OFFLINE)
                .samplingIntervalSec(60)
                .calibration(new Sensor.Calibration(0d, 1d))
                .enabled(true)
                .build());
    }

    public Actuator actuator(String gardenId, String channel, ActuatorType type) {
        return actuatorRepository.save(Actuator.builder()
                .gardenId(gardenId)
                .deviceCode("ESP32-A1")
                .channel(channel)
                .type(type)
                .name(type.getLabel())
                .state(type == ActuatorType.CURTAIN ? ActuatorState.CLOSED : ActuatorState.OFF)
                .maxRuntimeMinutes(30)
                .cooldownMinutes(0)
                .enabled(true)
                .build());
    }
}
