package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.AutomationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AutomationEventRepository extends MongoRepository<AutomationEvent, String> {

    Page<AutomationEvent> findByGardenIdOrderByOccurredAtDesc(String gardenId, Pageable pageable);

    List<AutomationEvent> findTop20ByGardenIdOrderByOccurredAtDesc(String gardenId);

    Page<AutomationEvent> findByGardenIdAndSensorIdOrderByOccurredAtDesc(
            String gardenId, String sensorId, Pageable pageable);
}
