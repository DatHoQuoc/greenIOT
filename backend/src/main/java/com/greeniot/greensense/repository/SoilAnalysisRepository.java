package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.SoilAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SoilAnalysisRepository extends MongoRepository<SoilAnalysis, String> {

    Optional<SoilAnalysis> findFirstByGardenIdOrderByMeasuredAtDesc(String gardenId);

    List<SoilAnalysis> findByGardenIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            String gardenId, Instant from, Instant to);
}
