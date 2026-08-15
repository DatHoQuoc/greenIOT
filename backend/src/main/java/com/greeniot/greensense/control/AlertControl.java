package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.AlertDtos;
import com.greeniot.greensense.boundary.ws.RealtimeBoundary;
import com.greeniot.greensense.common.config.GreenSenseProperties;
import com.greeniot.greensense.common.dto.PageResponse;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.Alert;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.AlertStatus;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * CONTROL — raises, de-duplicates and resolves alerts.
 *
 * <p>De-duplication matters: a soil probe reporting every 30 s while the plot is dry
 * would otherwise raise 120 identical alerts per hour. The same {@code code} is
 * suppressed for {@code greensense.automation.alert-dedupe-minutes}.
 */
@Service
@RequiredArgsConstructor
public class AlertControl {

    private final AlertRepository alertRepository;
    private final RealtimeBoundary realtimeBoundary;
    private final AutomationEventControl eventControl;
    private final NotificationControl notificationControl;
    private final GreenSenseProperties properties;

    /**
     * Compares a fresh reading against the garden threshold and raises an alert when the
     * band is broken. No-op when the metric is inside its band or has no threshold set.
     */
    @Transactional
    public void checkThreshold(Sensor sensor, double value, Threshold threshold) {
        if (threshold == null || !threshold.isBreached(value)) {
            // This probe recovered — close only ITS alerts, never a sibling probe's.
            resolveOpen(sensor.getGardenId(), codeFor(sensor, "LOW"), sensor.getId());
            resolveOpen(sensor.getGardenId(), codeFor(sensor, "HIGH"), sensor.getId());
            return;
        }

        boolean low = threshold.isBelowWarn(value) || (threshold.getMin() != null && value < threshold.getMin());
        String code = codeFor(sensor, low ? "LOW" : "HIGH");
        AlertSeverity severity = threshold.isCritical(value) ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        Double limit = low ? threshold.getWarnLow() : threshold.getWarnHigh();

        String direction = low ? "thấp hơn" : "vượt";
        String unit = sensor.getUnit() == null ? "" : sensor.getUnit();
        String message = "%s hiện %.1f%s, %s ngưỡng %s%s"
                .formatted(sensor.getType().getLabel(), value, unit, direction,
                        limit == null ? "cho phép" : trim(limit), unit);

        raise(Alert.builder()
                .gardenId(sensor.getGardenId())
                .sensorId(sensor.getId())
                .code(code)
                .severity(severity)
                .title(sensor.getType().getLabel() + (low ? " xuống thấp" : " lên cao"))
                .message(message)
                .triggerValue(value)
                .thresholdValue(limit)
                .unit(sensor.getUnit())
                .build());
    }

    /**
     * Raises an alert unless the same {@code (code, sensorId)} pair already fired inside
     * the dedupe window. Also hands the alert to {@link NotificationControl}, which decides
     * whether the owner actually gets pinged.
     */
    @Transactional
    public Alert raise(Alert alert) {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofMinutes(properties.getAutomation().getAlertDedupeMinutes()));

        boolean duplicate = alert.getSensorId() == null
                ? alertRepository.existsByGardenIdAndCodeAndSensorIdIsNullAndRaisedAtAfter(
                        alert.getGardenId(), alert.getCode(), since)
                : alertRepository.existsByGardenIdAndCodeAndSensorIdAndRaisedAtAfter(
                        alert.getGardenId(), alert.getCode(), alert.getSensorId(), since);
        if (duplicate) {
            return null;
        }

        alert.setRaisedAt(now);
        alert.setStatus(AlertStatus.OPEN);
        alert.setRead(false);
        Alert saved = alertRepository.save(alert);

        long unread = alertRepository.countByGardenIdAndReadFalse(saved.getGardenId());
        realtimeBoundary.pushAlert(saved.getGardenId(), AlertDtos.AlertResponse.from(saved), unread);

        eventControl.record(
                saved.getGardenId(),
                TriggerSource.SYSTEM,
                EventCategory.ALERT,
                saved.getTitle(),
                saved.getMessage(),
                saved.getSeverity() == AlertSeverity.CRITICAL ? EventTone.RED : EventTone.AMBER);

        notificationControl.notifyAlert(saved);

        return saved;
    }

    /** Closes this sensor's still-open alerts with the given code — the condition cleared. */
    @Transactional
    public void resolveOpen(String gardenId, String code, String sensorId) {
        List<Alert> open = alertRepository
                .findByGardenIdAndCodeAndSensorIdAndStatus(gardenId, code, sensorId, AlertStatus.OPEN);
        if (open.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        open.forEach(alert -> {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
        });
        alertRepository.saveAll(open);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertDtos.AlertResponse> list(String gardenId, AlertStatus status,
                                                      boolean unreadOnly, Pageable pageable) {
        Page<Alert> page;
        if (unreadOnly) {
            page = alertRepository.findByGardenIdAndReadFalseOrderByRaisedAtDesc(gardenId, pageable);
        } else if (status != null) {
            page = alertRepository.findByGardenIdAndStatusOrderByRaisedAtDesc(gardenId, status, pageable);
        } else {
            page = alertRepository.findByGardenIdOrderByRaisedAtDesc(gardenId, pageable);
        }
        return PageResponse.from(page, AlertDtos.AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String gardenId) {
        return alertRepository.countByGardenIdAndReadFalse(gardenId);
    }

    @Transactional
    public AlertDtos.AlertResponse markRead(String gardenId, String alertId) {
        Alert alert = require(gardenId, alertId);
        alert.setRead(true);
        return AlertDtos.AlertResponse.from(alertRepository.save(alert));
    }

    @Transactional
    public long markAllRead(String gardenId) {
        List<Alert> unread = alertRepository.findByGardenIdAndReadFalse(gardenId);
        unread.forEach(alert -> alert.setRead(true));
        alertRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public AlertDtos.AlertResponse acknowledge(String gardenId, String alertId) {
        Alert alert = require(gardenId, alertId);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        alert.setRead(true);
        return AlertDtos.AlertResponse.from(alertRepository.save(alert));
    }

    private Alert require(String gardenId, String alertId) {
        return alertRepository.findByIdAndGardenId(alertId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
    }

    private static String codeFor(Sensor sensor, String suffix) {
        return sensor.getType().name() + "_" + suffix;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
