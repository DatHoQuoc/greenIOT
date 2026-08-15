package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.AutomationRule;
import com.greeniot.greensense.entity.enums.SensorType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationRuleRepository extends MongoRepository<AutomationRule, String> {

    List<AutomationRule> findByGardenId(String gardenId);

    Optional<AutomationRule> findByIdAndGardenId(String id, String gardenId);

    /** Rules the ingestion path must evaluate for one incoming metric, best priority first. */
    List<AutomationRule> findByGardenIdAndEnabledTrueAndConditionSensorTypeOrderByPriorityAsc(
            String gardenId, SensorType sensorType);
}
