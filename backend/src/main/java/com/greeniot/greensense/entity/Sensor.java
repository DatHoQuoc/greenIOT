package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.SensorStatus;
import com.greeniot.greensense.entity.enums.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** ENTITY — one physical probe attached to a device node. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "sensors")
@CompoundIndex(name = "uk_sensor_channel", def = "{'gardenId':1,'deviceCode':1,'channel':1}", unique = true)
@CompoundIndex(name = "ix_sensor_type", def = "{'gardenId':1,'type':1}")
public class Sensor extends BaseDocument {

    @Indexed
    private String gardenId;

    /** Node identifier used in the MQTT topic, e.g. "ESP32-A1". */
    private String deviceCode;

    /** Port/channel on that node, e.g. "soil-1". Unique together with deviceCode. */
    private String channel;

    private SensorType type;

    private String name;

    private String unit;

    @Builder.Default
    private SensorStatus status = SensorStatus.OFFLINE;

    private Double lastValue;

    private Instant lastReadingAt;

    private Integer batteryLevel;

    private String firmwareVersion;

    @Builder.Default
    private Calibration calibration = new Calibration(0d, 1d);

    /** Expected publish cadence; drives the offline sweep. */
    @Builder.Default
    private Integer samplingIntervalSec = 60;

    @Builder.Default
    private boolean enabled = true;

    /** Applies linear calibration to a raw device value. */
    public double calibrate(double raw) {
        if (calibration == null) {
            return raw;
        }
        double scale = calibration.getScale() == null ? 1d : calibration.getScale();
        double offset = calibration.getOffset() == null ? 0d : calibration.getOffset();
        return raw * scale + offset;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Calibration {
        private Double offset;
        private Double scale;
    }
}
