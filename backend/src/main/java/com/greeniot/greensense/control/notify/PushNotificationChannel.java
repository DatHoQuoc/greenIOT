package com.greeniot.greensense.control.notify;

import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Device-push transport.
 *
 * <p>Unconfigured by design: no FCM service-account key ships with this repo, so
 * {@link #isAvailable()} reports false whenever the user has no registered device token
 * and the control skips it. The class marks exactly where the FCM/APNs call goes, and it
 * keeps {@code User.pushTokens} meaningful — that field was previously dead weight.
 */
@Slf4j
@Component
@Order(10)
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public String name() {
        return "push";
    }

    @Override
    public boolean isAvailable() {
        // Flip to a credentials check once an FCM key is provisioned.
        return false;
    }

    @Override
    public boolean send(User recipient, Alert alert) {
        if (recipient.getPushTokens() == null || recipient.getPushTokens().isEmpty()) {
            return false;
        }
        // TODO: POST to FCM https://fcm.googleapis.com/v1/projects/{id}/messages:send
        log.debug("Push transport not configured; {} token(s) skipped for alert {}",
                recipient.getPushTokens().size(), alert.getId());
        return false;
    }
}
