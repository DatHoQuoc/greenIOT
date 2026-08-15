package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.SensorDailyStat;
import com.greeniot.greensense.entity.enums.SensorType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SensorDailyStatRepository extends MongoRepository<SensorDailyStat, String> {

    Optional<SensorDailyStat> findBySensorIdAndDay(String sensorId, LocalDate day);

    List<SensorDailyStat> findByGardenIdAndTypeAndDayBetweenOrderByDayAsc(
            String gardenId, SensorType type, LocalDate from, LocalDate to);
}
