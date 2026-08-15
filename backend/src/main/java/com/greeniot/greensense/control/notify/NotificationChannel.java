package com.greeniot.greensense.control.notify;

import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.User;

/**
 * Delivery port for a single transport (push, email, SMS…).
 *
 * <p>Kept as an interface so wiring a real FCM/SMTP client later is an added
 * implementation rather than an edit to {@link com.greeniot.greensense.control.NotificationControl}.
 * Whether the user <i>wants</i> this transport is decided by the control, not here — a
 * channel only answers "can I send at all" via {@link #isAvailable()}.
 */
public interface NotificationChannel {

    /** Stable name used in logs and in {@code NotificationDelivery.channel}. */
    String name();

    /** False when the transport is not configured (no FCM key, no SMTP host…). */
    boolean isAvailable();

    /**
     * @return true when the message was handed to the transport. Implementations must not
     *         throw: a dead mail server must never break the ingestion path that raised
     *         the alert.
     */
    boolean send(User recipient, Alert alert);
}
