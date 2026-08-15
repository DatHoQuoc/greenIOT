package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.SensorReading;
import com.greeniot.greensense.entity.enums.SensorType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Time-series collection gateway. Derived queries address the nested {@code meta} field;
 * everything statistical lives in {@link SensorReadingRepositoryCustom}.
 */
public interface SensorReadingRepository
        extends MongoRepository<SensorReading, String>, SensorReadingRepositoryCustom {

    Optional<SensorReading> findFirstByMetaSensorIdOrderByTimestampDesc(String sensorId);

    List<SensorReading> findByMetaSensorIdAndTimestampBetweenOrderByTimestampAsc(
            String sensorId, Instant from, Instant to);

    @Query(value = "{ 'meta.gardenId': ?0, 'meta.type': ?1, 'timestamp': { $gte: ?2, $lte: ?3 } }",
            sort = "{ 'timestamp': 1 }")
    List<SensorReading> findGardenSeries(String gardenId, SensorType type, Instant from, Instant to);

    long countByMetaGardenIdAndTimestampBetween(String gardenId, Instant from, Instant to);
}
