package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.RealtimeDtos;
import com.greeniot.greensense.boundary.ws.RealtimeBoundary;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.SensorReading;
import com.greeniot.greensense.entity.Threshold;
import com.greeniot.greensense.entity.enums.AlertSeverity;
import com.greeniot.greensense.entity.enums.MeasurementSource;
import com.greeniot.greensense.entity.enums.ReadingQuality;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.SensorReadingRepository;
import com.greeniot.greensense.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * CONTROL — the hot path. Every measurement, whether it arrives over MQTT or HTTP,
 * lands here.
 *
 * <p>Steps: resolve the sensor from (garden, device, channel) → calibrate → quality-check →
 * persist → refresh the sensor's cached last value → push to open browsers →
 * evaluate thresholds → evaluate automation rules. A pH reading additionally produces a
 * {@code SoilAnalysis} snapshot so the soil screen always has fresh advice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestControl {

    /** Values outside these bounds are physically impossible and get flagged, not stored as truth. */
    private static final Map<SensorType, double[]> PLAUSIBLE = new EnumMap<>(Map.of(
            SensorType.TEMPERATURE, new double[]{-30d, 80d},
            SensorType.AIR_HUMIDITY, new double[]{0d, 100d},
            SensorType.SOIL_MOISTURE, new double[]{0d, 100d},
            SensorType.LIGHT, new double[]{0d, 200_000d},
            SensorType.PH, new double[]{0d, 14d}));

    /** Re-analysing soil on every pH sample would flood the collection; once an hour is enough. */
    private static final long SOIL_ANALYSIS_MIN_GAP_SECONDS = 3600;

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final GardenRepository gardenRepository;
    private final RealtimeBoundary realtimeBoundary;
    private final AlertControl alertControl;
    private final RuleEngineControl ruleEngineControl;
    private final SoilAdvisoryControl soilAdvisoryControl;

    /**
     * @return the stored reading, or empty when the device/channel is not registered —
     *         unknown hardware is dropped, never auto-provisioned
     */
    @Transactional
    public Optional<SensorReading> ingest(String gardenId, String deviceCode, String channel,
                                          double rawValue, Instant timestamp) {

        Optional<Sensor> found = sensorRepository
                .findByGardenIdAndDeviceCodeAndChannel(gardenId, deviceCode, channel);
        if (found.isEmpty()) {
            log.warn("Telemetry from unregistered channel {}/{} in garden {}", deviceCode, channel, gardenId);
            return Optional.empty();
        }

        Sensor sensor = found.get();
        if (!sensor.isEnabled()) {
            return Optional.empty();
        }

        Garden garden = gardenRepository.findById(gardenId).orElse(null);
        if (garden == null) {
            log.warn("Telemetry for unknown garden {}", gardenId);
            return Optional.empty();
        }

        Instant when = timestamp == null ? Instant.now() : timestamp;
        double value = round(sensor.calibrate(rawValue));
        ReadingQuality quality = qualityOf(sensor.getType(), value);

        SensorReading reading = readingRepository.save(SensorReading.builder()
                .timestamp(when)
                .meta(SensorReading.Meta.builder()
                        .gardenId(gardenId)
                        .sensorId(sensor.getId())
                        .type(sensor.getType())
                        .build())
                .value(value)
                .unit(sensor.getUnit())
                .quality(quality)
                .build());

        sensor.setLastValue(value);
        sensor.setLastReadingAt(when);
        sensor.setStatus(SensorStatus.ONLINE);
        sensorRepository.save(sensor);

        Threshold threshold = garden.thresholdFor(sensor.getType());
        boolean breached = threshold != null && threshold.isBreached(value);

        realtimeBoundary.pushReading(gardenId, new RealtimeDtos.ReadingPush(
                sensor.getId(), sensor.getType(), sensor.getType().getSlug(),
                value, sensor.getUnit(), when, breached));

        // Neither a BAD nor a SUSPECT sample may drive automation.
        //
        // BAD is physically impossible, so it is stored for diagnosis and nothing else.
        // SUSPECT means the value sits exactly on the rail (0 % or full scale), which is
        // what a disconnected probe reports — treating it as real would start a pump
        // because a cable fell out. Both raise a fault alert instead, so the operator
        // learns the probe needs attention rather than silently losing automation.
        if (quality != ReadingQuality.GOOD) {
            log.warn("{} {} reading {} from sensor {}", quality, sensor.getType(), value, sensor.getId());
            raiseSensorFault(sensor, value, quality);
            return Optional.of(reading);
        }

        alertControl.checkThreshold(sensor, value, threshold);
        ruleEngineControl.evaluate(garden, sensor, value);

        if (sensor.getType() == SensorType.PH) {
            maybeAnalyseSoil(garden, sensor, value, when);
        }

        return Optional.of(reading);
    }

    /** Device status frame: battery, firmware, online flag. */
    @Transactional
    public void ingestStatus(String gardenId, String deviceCode, Boolean online, Integer battery, String firmware) {
        var sensors = sensorRepository.findByGardenId(gardenId).stream()
                .filter(sensor -> deviceCode.equals(sensor.getDeviceCode()))
                .toList();

        for (Sensor sensor : sensors) {
            if (battery != null) {
                sensor.setBatteryLevel(battery);
            }
            if (firmware != null) {
                sensor.setFirmwareVersion(firmware);
            }
            if (online != null) {
                sensor.setStatus(online ? SensorStatus.ONLINE : SensorStatus.OFFLINE);
            }
        }
        sensorRepository.saveAll(sensors);
    }

    /** Tells the operator the probe is untrustworthy, instead of silently dropping automation. */
    private void raiseSensorFault(Sensor sensor, double value, ReadingQuality quality) {
        boolean bad = quality == ReadingQuality.BAD;
        alertControl.raise(com.greeniot.greensense.entity.Alert.builder()
                .gardenId(sensor.getGardenId())
                .sensorId(sensor.getId())
                .code("SENSOR_" + quality.name())
                .severity(bad ? AlertSeverity.CRITICAL : AlertSeverity.WARNING)
                .title("Cảm biến " + sensor.getName() + " báo giá trị bất thường")
                .message(bad
                        ? "Giá trị %.2f%s nằm ngoài dải vật lý — kiểm tra dây và nguồn cảm biến."
                                .formatted(value, nullSafe(sensor.getUnit()))
                        : "Giá trị %.2f%s đang ở sát biên đo — có thể cảm biến bị tuột dây. Tự động hóa tạm dừng cho cảm biến này."
                                .formatted(value, nullSafe(sensor.getUnit())))
                .triggerValue(value)
                .unit(sensor.getUnit())
                .build());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void maybeAnalyseSoil(Garden garden, Sensor sensor, double ph, Instant when) {
        var latest = soilAdvisoryControl.latest(garden.getId());
        boolean stale = latest == null
                || latest.measuredAt() == null
                || latest.measuredAt().plusSeconds(SOIL_ANALYSIS_MIN_GAP_SECONDS).isBefore(when);

        if (stale) {
            soilAdvisoryControl.analyse(garden.getId(), sensor.getId(), ph, when, MeasurementSource.SENSOR);
        }
    }

    private static ReadingQuality qualityOf(SensorType type, double value) {
        double[] bounds = PLAUSIBLE.get(type);
        if (bounds == null) {
            return ReadingQuality.GOOD;
        }
        if (value < bounds[0] || value > bounds[1]) {
            return ReadingQuality.BAD;
        }
        // Right at the rail usually means a disconnected probe reading 0 or full scale.
        if (value == bounds[0] || value == bounds[1]) {
            return ReadingQuality.SUSPECT;
        }
        return ReadingQuality.GOOD;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
