package com.greeniot.greensense.control;

import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.security.JwtProperties;
import com.greeniot.greensense.entity.RefreshToken;
import com.greeniot.greensense.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * CONTROL — issues, rotates and revokes refresh tokens.
 *
 * <p>Rotation with reuse detection: every successful refresh mints a new token and revokes
 * the presented one. If a <i>revoked</i> token is later presented, the only explanations
 * are a stolen copy or a replay, so the whole family for that user is revoked and the
 * session dies. That is the standard mitigation for a refresh token that leaves the
 * browser's control.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenControl {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /** The raw token goes to the client once; only its hash is ever stored. */
    public record IssuedToken(String rawToken, Instant expiresAt) {
    }

    @Transactional
    public IssuedToken issue(String userId, String userAgent) {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(jwtProperties.getRefreshExpirationDays()));

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(raw))
                .userId(userId)
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .build());

        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Consumes a refresh token and mints its replacement.
     *
     * @return the user id the token belonged to, plus the new token
     */
    @Transactional
    public RotationResult rotate(String rawToken, String userAgent) {
        String presentedHash = hash(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new BusinessRuleException("INVALID_REFRESH_TOKEN", "Refresh token not recognised"));

        Instant now = Instant.now();

        if (stored.isRevoked()) {
            // Reuse of a spent token: assume compromise and kill every live session.
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", stored.getUserId());
            revokeAllForUser(stored.getUserId());
            throw new BusinessRuleException("REFRESH_TOKEN_REUSED",
                    "This session was terminated for security reasons. Please sign in again.");
        }

        if (!stored.isUsable(now)) {
            throw new BusinessRuleException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        IssuedToken replacement = issue(stored.getUserId(), userAgent);

        stored.setRevoked(true);
        stored.setRevokedAt(now);
        stored.setReplacedByHash(hash(replacement.rawToken()));
        refreshTokenRepository.save(stored);

        return new RotationResult(stored.getUserId(), replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    /** Used on logout-everywhere, password change and reuse detection. */
    @Transactional
    public void revokeAllForUser(String userId) {
        List<RefreshToken> live = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        Instant now = Instant.now();
        live.forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
        });
        refreshTokenRepository.saveAll(live);
    }

    /** Expired rows are dead weight — they cannot authenticate anything. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now().minus(Duration.ofDays(7)));
    }

    public record RotationResult(String userId, IssuedToken token) {
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
