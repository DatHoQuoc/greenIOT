package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.SensorDtos;
import com.greeniot.greensense.common.config.GreenSenseProperties;
import com.greeniot.greensense.common.exception.BusinessRuleException;
import com.greeniot.greensense.common.exception.ResourceNotFoundException;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** CONTROL — sensor registry and health. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorControl {

    private final SensorRepository sensorRepository;
    private final GreenSenseProperties properties;

    @Transactional(readOnly = true)
    public List<SensorDtos.SensorResponse> list(String gardenId, SensorType type) {
        List<Sensor> sensors = type == null
                ? sensorRepository.findByGardenId(gardenId)
                : sensorRepository.findByGardenIdAndType(gardenId, type);
        return sensors.stream().map(SensorDtos.SensorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SensorDtos.SensorResponse get(String gardenId, String sensorId) {
        return SensorDtos.SensorResponse.from(require(gardenId, sensorId));
    }

    @Transactional
    public SensorDtos.SensorResponse register(String gardenId, SensorDtos.RegisterSensorRequest request) {
        sensorRepository.findByGardenIdAndDeviceCodeAndChannel(
                gardenId, request.deviceCode(), request.channel()).ifPresent(existing -> {
            throw new BusinessRuleException("SENSOR_EXISTS",
                    "A sensor is already registered on " + request.deviceCode() + "/" + request.channel());
        });

        Sensor sensor = Sensor.builder()
                .gardenId(gardenId)
                .deviceCode(request.deviceCode())
                .channel(request.channel())
                .type(request.type())
                .name(StringUtils.hasText(request.name()) ? request.name() : request.type().getLabel())
                .unit(StringUtils.hasText(request.unit()) ? request.unit() : request.type().getDefaultUnit())
                .samplingIntervalSec(request.samplingIntervalSec() == null ? 60 : request.samplingIntervalSec())
                .calibration(new Sensor.Calibration(
                        request.calibrationOffset() == null ? 0d : request.calibrationOffset(),
                        request.calibrationScale() == null ? 1d : request.calibrationScale()))
                .status(SensorStatus.OFFLINE)
                .enabled(true)
                .build();

        return SensorDtos.SensorResponse.from(sensorRepository.save(sensor));
    }

    @Transactional
    public SensorDtos.SensorResponse update(String gardenId, String sensorId,
                                            SensorDtos.UpdateSensorRequest request) {
        Sensor sensor = require(gardenId, sensorId);

        if (StringUtils.hasText(request.name())) {
            sensor.setName(request.name());
        }
        if (StringUtils.hasText(request.unit())) {
            sensor.setUnit(request.unit());
        }
        if (request.samplingIntervalSec() != null) {
            sensor.setSamplingIntervalSec(request.samplingIntervalSec());
        }
        if (request.calibrationOffset() != null || request.calibrationScale() != null) {
            Sensor.Calibration calibration = sensor.getCalibration() == null
                    ? new Sensor.Calibration(0d, 1d) : sensor.getCalibration();
            if (request.calibrationOffset() != null) {
                calibration.setOffset(request.calibrationOffset());
            }
            if (request.calibrationScale() != null) {
                calibration.setScale(request.calibrationScale());
            }
            sensor.setCalibration(calibration);
        }
        if (request.enabled() != null) {
            sensor.setEnabled(request.enabled());
        }
        return SensorDtos.SensorResponse.from(sensorRepository.save(sensor));
    }

    @Transactional
    public void delete(String gardenId, String sensorId) {
        sensorRepository.delete(require(gardenId, sensorId));
    }

    @Transactional(readOnly = true)
    public Sensor require(String gardenId, String sensorId) {
        return sensorRepository.findByIdAndGardenId(sensorId, gardenId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
    }

    /**
     * Flips silent sensors to OFFLINE so the dashboard stops showing a stale value as live.
     * A sensor is silent when it has missed {@code offline-factor} publish intervals.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void sweepOffline() {
        Instant now = Instant.now();
        int factor = Math.max(2, properties.getReadings().getOfflineFactor());

        // The widest possible window; each sensor is then re-checked against its own interval.
        Instant widest = now.minus(Duration.ofSeconds(60L * factor));
        List<Sensor> stale = new ArrayList<>();

        for (Sensor sensor : sensorRepository.findByEnabledTrueAndLastReadingAtBefore(widest)) {
            if (sensor.getStatus() == SensorStatus.OFFLINE || sensor.getLastReadingAt() == null) {
                continue;
            }
            int interval = sensor.getSamplingIntervalSec() == null ? 60 : sensor.getSamplingIntervalSec();
            Instant deadline = sensor.getLastReadingAt().plus(Duration.ofSeconds((long) interval * factor));
            if (deadline.isBefore(now)) {
                sensor.setStatus(SensorStatus.OFFLINE);
                stale.add(sensor);
            }
        }

        if (!stale.isEmpty()) {
            sensorRepository.saveAll(stale);
            log.info("Marked {} sensor(s) offline", stale.size());
        }
    }
}
