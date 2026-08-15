package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.Garden;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GardenRepository extends MongoRepository<Garden, String> {

    List<Garden> findByOwnerIdOrderByCreatedAtAsc(String ownerId);

    Optional<Garden> findByIdAndOwnerId(String id, String ownerId);

    /**
     * Gardens the user can see: their own plus any they were added to.
     *
     * <p>Hand-written because the condition is an {@code $or} across a scalar field and an
     * array element — Spring Data cannot derive that from a method name.
     */
    @Query(value = "{ $or: [ { 'ownerId': ?0 }, { 'members.userId': ?0 } ] }",
            sort = "{ 'createdAt': 1 }")
    List<Garden> findAllAccessibleBy(String userId);

    @Query("{ '_id': ?0, $or: [ { 'ownerId': ?1 }, { 'members.userId': ?1 } ] }")
    Optional<Garden> findAccessibleById(String gardenId, String userId);
}
