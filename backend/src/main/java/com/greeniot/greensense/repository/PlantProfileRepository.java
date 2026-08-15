package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.PlantProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PlantProfileRepository extends MongoRepository<PlantProfile, String> {

    Optional<PlantProfile> findByName(String name);
}
