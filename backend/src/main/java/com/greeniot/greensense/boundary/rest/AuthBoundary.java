package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.boundary.dto.AuthDtos;
import com.greeniot.greensense.common.dto.ApiResponse;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.security.JwtProperties;
import com.greeniot.greensense.common.security.SecurityUtils;
import com.greeniot.greensense.control.AuthControl;
import com.greeniot.greensense.control.RefreshTokenControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * BOUNDARY — authentication.
 *
 * <p>The refresh token never appears in a response body; it is set as an HttpOnly cookie
 * so page scripts cannot read it. The access token is returned in the body for the client
 * to keep in memory only.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthBoundary {

    private final AuthControl authControl;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    @Operation(summary = "Create an account, return an access token and set the refresh cookie")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest httpRequest) {

        AuthControl.Session session = authControl.register(request, userAgent(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(ApiResponse.ok(session.response()));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access token and a refresh cookie")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthControl.Session session = authControl.login(request, userAgent(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(ApiResponse.ok(session.response()));
    }

    /**
     * Silent refresh. Reads the cookie, rotates it, returns a new access token.
     * Public in the security config — the cookie <i>is</i> the credential here.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and mint a new access token")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> refresh(
            @CookieValue(name = "${greensense.security.jwt.refresh-cookie-name:gs_refresh}", required = false)
            String refreshToken,
            HttpServletRequest httpRequest) {

        // 401, không phải 409: đây là "chưa có thông tin xác thực", trạng thái hoàn toàn
        // bình thường khi mở trang lần đầu. Trả 409 khiến console của người dùng đỏ lên
        // trong một tình huống không có gì sai, và client không phân biệt được với lỗi thật.
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(new ApiResponse.ApiError(
                            "NO_REFRESH_TOKEN", "Chưa đăng nhập", null)));
        }

        AuthControl.Session session = authControl.refresh(refreshToken, userAgent(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(ApiResponse.ok(session.response()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "${greensense.security.jwt.refresh-cookie-name:gs_refresh}", required = false)
            String refreshToken) {

        authControl.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(ApiResponse.ok());
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the authenticated caller")
    public ApiResponse<AuthDtos.UserResponse> me() {
        return ApiResponse.ok(authControl.profile(SecurityUtils.requireUserId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update name, phone and notification preferences")
    public ApiResponse<AuthDtos.UserResponse> updateProfile(
            @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return ApiResponse.ok(authControl.updateProfile(SecurityUtils.requireUserId(), request));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change password — ends every other session")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {

        authControl.changePassword(SecurityUtils.requireUserId(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/me/push-tokens")
    @Operation(summary = "Register a device token for push notifications")
    public ApiResponse<AuthDtos.UserResponse> addPushToken(
            @Valid @RequestBody AuthDtos.RegisterPushTokenRequest request) {
        return ApiResponse.ok(authControl.registerPushToken(SecurityUtils.requireUserId(), request.token()));
    }

    @DeleteMapping("/me/push-tokens")
    public ApiResponse<AuthDtos.UserResponse> removePushToken(
            @Valid @RequestBody AuthDtos.RegisterPushTokenRequest request) {
        return ApiResponse.ok(authControl.removePushToken(SecurityUtils.requireUserId(), request.token()));
    }

    private ResponseCookie refreshCookie(RefreshTokenControl.IssuedToken token) {
        return baseCookie(token.rawToken())
                .maxAge(Duration.between(Instant.now(), token.expiresAt()))
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                // Scoped to /api/v1/auth so the cookie is not attached to every data call.
                .path("/api/v1/auth");
    }

    private static String userAgent(HttpServletRequest request) {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        return agent == null ? null : agent.substring(0, Math.min(agent.length(), 250));
    }
}
