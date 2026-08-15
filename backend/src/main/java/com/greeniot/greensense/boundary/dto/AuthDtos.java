package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Request/response shapes for {@code /api/v1/auth}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @NotBlank String fullName,
            String phone) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /**
     * The refresh token is deliberately absent: it travels as an HttpOnly cookie so
     * JavaScript — and therefore any XSS on the page — cannot read it. The access token is
     * returned in the body for the client to hold in memory only.
     */
    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String newPassword) {
    }

    public record UpdateProfileRequest(
            String fullName,
            String phone,
            Boolean notifyByPush,
            Boolean notifyByEmail,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd) {
    }

    public record RegisterPushTokenRequest(@NotBlank String token) {
    }

    public record UserResponse(
            String id,
            String email,
            String fullName,
            String phone,
            String role,
            boolean notifyByPush,
            boolean notifyByEmail,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            List<String> pushTokens) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getRole().name(),
                    user.isNotifyByPush(),
                    user.isNotifyByEmail(),
                    user.getQuietHoursStart(),
                    user.getQuietHoursEnd(),
                    user.getPushTokens());
        }
    }
}
