package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.SensorType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorRepository extends MongoRepository<Sensor, String> {

    List<Sensor> findByGardenId(String gardenId);

    List<Sensor> findByGardenIdAndType(String gardenId, SensorType type);

    Optional<Sensor> findByGardenIdAndDeviceCodeAndChannel(String gardenId, String deviceCode, String channel);

    Optional<Sensor> findByIdAndGardenId(String id, String gardenId);

    long countByGardenId(String gardenId);

    /** Candidates for the offline sweep: enabled sensors that have gone quiet. */
    List<Sensor> findByEnabledTrueAndLastReadingAtBefore(Instant cutoff);
}
