package com.greeniot.greensense.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "greensense.security.jwt")
public class JwtProperties {

    /** Base64-encoded HMAC key. Override with JWT_SECRET in every non-dev environment. */
    private String secret;

    /**
     * Access-token lifetime. Short by design — the refresh token carries the session, and
     * a leaked access token then expires on its own rather than living for a day.
     */
    private long expirationMinutes = 30;

    /** Refresh-token lifetime; also how long "stay signed in" lasts. */
    private long refreshExpirationDays = 30;

    private String issuer = "greensense";

    /** Name of the HttpOnly cookie carrying the refresh token. */
    private String refreshCookieName = "gs_refresh";

    /** Send the refresh cookie only over HTTPS. Must be true in production. */
    private boolean refreshCookieSecure = false;

    /** {@code Lax} works for same-site; cross-site frontends need {@code None} + secure. */
    private String refreshCookieSameSite = "Lax";
}
