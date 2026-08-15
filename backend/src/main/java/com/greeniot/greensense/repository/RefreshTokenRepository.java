package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalse(String userId);

    /** Housekeeping: tokens that expired long ago carry no value, revoked or not. */
    void deleteByExpiresAtBefore(Instant cutoff);
}
