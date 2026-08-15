package com.greeniot.greensense.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Reads the current principal without threading it through every method signature. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static String requireUserId() {
        return currentUser()
                .map(UserPrincipal::getId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "No authenticated user in context"));
    }
}
