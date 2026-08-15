package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.IrrigationSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IrrigationScheduleRepository extends MongoRepository<IrrigationSchedule, String> {

    List<IrrigationSchedule> findByGardenId(String gardenId);

    Optional<IrrigationSchedule> findByIdAndGardenId(String id, String gardenId);

    List<IrrigationSchedule> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
}
