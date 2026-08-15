package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.FertilizerApplication;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FertilizerApplicationRepository extends MongoRepository<FertilizerApplication, String> {

    Optional<FertilizerApplication> findByGardenIdAndAppliedOn(String gardenId, LocalDate appliedOn);

    List<FertilizerApplication> findTop30ByGardenIdOrderByAppliedOnDesc(String gardenId);
}
