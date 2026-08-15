package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends MongoRepository<Alert, String> {

    Page<Alert> findByGardenIdOrderByRaisedAtDesc(String gardenId, Pageable pageable);

    Page<Alert> findByGardenIdAndStatusOrderByRaisedAtDesc(String gardenId, AlertStatus status, Pageable pageable);

    Page<Alert> findByGardenIdAndReadFalseOrderByRaisedAtDesc(String gardenId, Pageable pageable);

    Optional<Alert> findByIdAndGardenId(String id, String gardenId);

    long countByGardenIdAndReadFalse(String gardenId);

    List<Alert> findByGardenIdAndReadFalse(String gardenId);

    /**
     * De-duplication probe scoped to ONE sensor.
     *
     * <p>Keying on {@code (gardenId, code)} alone was wrong: a garden with four soil probes
     * shares the code {@code SOIL_MOISTURE_LOW}, so one dry probe suppressed the alerts of
     * the other three for the whole dedupe window.
     */
    boolean existsByGardenIdAndCodeAndSensorIdAndRaisedAtAfter(
            String gardenId, String code, String sensorId, Instant since);

    /** Same reasoning as above, for alerts that belong to no particular sensor. */
    boolean existsByGardenIdAndCodeAndSensorIdIsNullAndRaisedAtAfter(
            String gardenId, String code, Instant since);

    List<Alert> findByGardenIdAndCodeAndSensorIdAndStatus(
            String gardenId, String code, String sensorId, AlertStatus status);
}
