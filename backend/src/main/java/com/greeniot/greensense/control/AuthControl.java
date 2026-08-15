package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.AuthDtos;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.common.security.JwtService;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.UserRole;
import com.greeniot.greensense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

/** CONTROL — registration, login, session rotation and account self-service. */
@Service
@RequiredArgsConstructor
public class AuthControl {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenControl refreshTokenControl;

    /** Access token plus the raw refresh token the boundary turns into a cookie. */
    public record Session(AuthDtos.AuthResponse response, RefreshTokenControl.IssuedToken refreshToken) {
    }

    @Transactional
    public Session register(AuthDtos.RegisterRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "This email is already registered");
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(UserRole.OWNER)
                .enabled(true)
                .build());

        return sessionFor(user, userAgent);
    }

    @Transactional
    public Session login(AuthDtos.LoginRequest request, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BusinessRuleException("ACCOUNT_DISABLED", "This account has been disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return sessionFor(user, userAgent);
    }

    /** Exchanges a refresh token for a fresh pair; the presented token is spent. */
    @Transactional
    public Session refresh(String rawRefreshToken, String userAgent) {
        RefreshTokenControl.RotationResult rotation = refreshTokenControl.rotate(rawRefreshToken, userAgent);
        User user = requireUser(rotation.userId());

        if (!user.isEnabled()) {
            refreshTokenControl.revokeAllForUser(user.getId());
            throw new BusinessRuleException("ACCOUNT_DISABLED", "This account has been disabled");
        }

        String accessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole().name());
        return new Session(
                new AuthDtos.AuthResponse(accessToken, "Bearer", jwtService.expiryOf(accessToken),
                        AuthDtos.UserResponse.from(user)),
                rotation.token());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenControl.revoke(rawRefreshToken);
        }
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse profile(String userId) {
        return AuthDtos.UserResponse.from(requireUser(userId));
    }

    @Transactional
    public AuthDtos.UserResponse updateProfile(String userId, AuthDtos.UpdateProfileRequest request) {
        User user = requireUser(userId);

        if (StringUtils.hasText(request.fullName())) {
            user.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.notifyByPush() != null) {
            user.setNotifyByPush(request.notifyByPush());
        }
        if (request.notifyByEmail() != null) {
            user.setNotifyByEmail(request.notifyByEmail());
        }
        // Quiet hours are a pair: one without the other is not a window, so both are
        // written together and clearing either clears both.
        if (request.quietHoursStart() != null && request.quietHoursEnd() != null) {
            user.setQuietHoursStart(request.quietHoursStart());
            user.setQuietHoursEnd(request.quietHoursEnd());
        } else if (request.quietHoursStart() == null && request.quietHoursEnd() == null) {
            user.setQuietHoursStart(null);
            user.setQuietHoursEnd(null);
        }

        return AuthDtos.UserResponse.from(userRepository.save(user));
    }

    /**
     * Changing a password ends every other session. Otherwise a password changed <i>because
     * it leaked</i> would leave the thief's refresh token working.
     */
    @Transactional
    public void changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        User user = requireUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("PASSWORD_UNCHANGED", "New password must differ from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenControl.revokeAllForUser(userId);
    }

    @Transactional
    public AuthDtos.UserResponse registerPushToken(String userId, String token) {
        User user = requireUser(userId);
        if (user.getPushTokens() == null) {
            user.setPushTokens(new ArrayList<>());
        }
        if (!user.getPushTokens().contains(token)) {
            user.getPushTokens().add(token);
        }
        return AuthDtos.UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AuthDtos.UserResponse removePushToken(String userId, String token) {
        User user = requireUser(userId);
        if (user.getPushTokens() != null) {
            user.getPushTokens().remove(token);
        }
        return AuthDtos.UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Session sessionFor(User user, String userAgent) {
        String accessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole().name());
        RefreshTokenControl.IssuedToken refreshToken = refreshTokenControl.issue(user.getId(), userAgent);

        return new Session(
                new AuthDtos.AuthResponse(accessToken, "Bearer", jwtService.expiryOf(accessToken),
                        AuthDtos.UserResponse.from(user)),
                refreshToken);
    }
}
