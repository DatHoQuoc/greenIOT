package com.greeniot.greensense.boundary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** Wire formats exchanged with device nodes over MQTT. Unknown fields are tolerated. */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    /** Payload of {@code greensense/{gardenId}/{deviceCode}/telemetry}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelemetryPayload(String channel, Double value, Instant ts, List<Sample> samples) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Sample(String channel, Double value, Instant ts) {
        }
    }

    /** Payload of {@code greensense/{gardenId}/{deviceCode}/status}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusPayload(Boolean online, Integer battery, String fw) {
    }

    /** Payload of {@code greensense/{gardenId}/{deviceCode}/ack}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AckPayload(String correlationId, String status, String state, String error) {
    }

    /** Payload the server publishes to {@code greensense/{gardenId}/{deviceCode}/command}. */
    public record CommandPayload(
            String correlationId,
            String channel,
            String command,
            Integer durationMinutes,
            Instant issuedAt) {
    }
}
