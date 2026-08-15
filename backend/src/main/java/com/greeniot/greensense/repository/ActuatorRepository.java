package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.enums.ActuatorType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActuatorRepository extends MongoRepository<Actuator, String> {

    List<Actuator> findByGardenId(String gardenId);

    List<Actuator> findByGardenIdAndType(String gardenId, ActuatorType type);

    Optional<Actuator> findByIdAndGardenId(String id, String gardenId);

    Optional<Actuator> findByGardenIdAndDeviceCodeAndChannel(String gardenId, String deviceCode, String channel);

    long countByGardenIdAndType(String gardenId, ActuatorType type);

    /** Devices past their auto-off deadline; the safety sweep forces them off. */
    List<Actuator> findByAutoOffAtBefore(Instant now);
}
