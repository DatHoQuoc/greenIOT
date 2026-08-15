package com.greeniot.greensense.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * ENTITY — a long-lived refresh token, stored hashed.
 *
 * <p>Only the SHA-256 of the token is persisted: a leaked database dump then yields no
 * usable sessions. Tokens are rotated on every refresh and the old one is revoked, so a
 * stolen token is good for at most one use — and reusing a revoked token is a detectable
 * signal that the family is compromised.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "refresh_tokens")
public class RefreshToken extends BaseDocument {

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    private Instant revokedAt;

    /** The token that replaced this one; lets a reuse attempt be traced to its family. */
    private String replacedByHash;

    private String userAgent;

    public boolean isUsable(Instant now) {
        return !revoked && expiresAt != null && expiresAt.isAfter(now);
    }
}
