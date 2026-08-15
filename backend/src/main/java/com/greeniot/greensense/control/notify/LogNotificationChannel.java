package com.greeniot.greensense.control.notify;

import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Default transport: writes the notification to the log.
 *
 * <p>This exists so the whole notification decision path — preferences, quiet hours,
 * severity escalation — is exercised and testable without an FCM project or an SMTP
 * server. Swap in a real channel by adding another {@link NotificationChannel} bean;
 * this one stays as the audit trail.
 */
@Slf4j
@Component
@Order(100)
public class LogNotificationChannel implements NotificationChannel {

    @Override
    public String name() {
        return "log";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean send(User recipient, Alert alert) {
        log.info("NOTIFY [{}] to {} — {} : {}",
                alert.getSeverity(), recipient.getEmail(), alert.getTitle(), alert.getMessage());
        return true;
    }
}
