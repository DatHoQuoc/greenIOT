package com.greeniot.greensense.control;

import com.greeniot.greensense.control.notify.NotificationChannel;
import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * CONTROL — decides whether a raised alert actually reaches a human, and over which
 * transport.
 *
 * <p>This is what makes {@code User.notifyByPush}, {@code notifyByEmail},
 * {@code pushTokens} and {@code quietHoursStart/End} mean something; before this class
 * they were stored and ignored.
 *
 * <p>Runs {@code @Async}: a slow transport must not stall the telemetry ingestion path
 * that raised the alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationControl {

    private final UserRepository userRepository;
    private final GardenRepository gardenRepository;
    private final List<NotificationChannel> channels;

    @Async
    public void notifyAlert(Alert alert) {
        if (alert == null) {
            return;
        }
        try {
            Garden garden = gardenRepository.findById(alert.getGardenId()).orElse(null);
            if (garden == null) {
                return;
            }
            User owner = userRepository.findById(garden.getOwnerId()).orElse(null);
            if (owner == null || !owner.isEnabled()) {
                return;
            }

            if (isSuppressedByQuietHours(owner, garden, alert)) {
                log.debug("Alert {} held back by quiet hours for {}", alert.getId(), owner.getEmail());
                return;
            }

            for (NotificationChannel channel : channels) {
                if (!wantsChannel(owner, channel)) {
                    continue;
                }
                if (!channel.isAvailable()) {
                    continue;
                }
                if (channel.send(owner, alert)) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            // Notification is best-effort; never let it surface into the caller.
            log.warn("Notification for alert {} failed: {}", alert.getId(), ex.getMessage());
        }
    }

    private boolean wantsChannel(User user, NotificationChannel channel) {
        return switch (channel.name()) {
            case "push" -> user.isNotifyByPush();
            case "email" -> user.isNotifyByEmail();
            // The log channel is the audit trail — always on.
            default -> true;
        };
    }

    /**
     * Quiet hours silence INFO and WARNING only. A CRITICAL alert — soil past the hard
     * bound, pump stuck on — is exactly what someone set an alarm for; holding it until
     * morning would be the wrong call.
     */
    private boolean isSuppressedByQuietHours(User user, Garden garden, Alert alert) {
        if (user.getQuietHoursStart() == null || user.getQuietHoursEnd() == null) {
            return false;
        }
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            return false;
        }
        return isWithin(LocalTime.now(zoneOf(garden)), user.getQuietHoursStart(), user.getQuietHoursEnd());
    }

    /** Window-aware comparison; {@code 22:00 → 06:00} wraps past midnight. */
    static boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private static ZoneId zoneOf(Garden garden) {
        try {
            return ZoneId.of(garden.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
