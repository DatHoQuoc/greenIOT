package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.DeviceCommand;
import com.greeniot.greensense.entity.enums.CommandStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceCommandRepository extends MongoRepository<DeviceCommand, String> {

    Optional<DeviceCommand> findByCorrelationId(String correlationId);

    List<DeviceCommand> findByStatusAndIssuedAtBefore(CommandStatus status, Instant cutoff);

    List<DeviceCommand> findTop20ByGardenIdOrderByIssuedAtDesc(String gardenId);
}
