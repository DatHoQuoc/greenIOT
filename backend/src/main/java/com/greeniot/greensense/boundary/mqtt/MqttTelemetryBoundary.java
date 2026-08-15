package com.greeniot.greensense.boundary.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greeniot.greensense.boundary.dto.DeviceDtos;
import com.greeniot.greensense.common.config.MqttConfig;
import com.greeniot.greensense.control.ActuatorControl;
import com.greeniot.greensense.control.TelemetryIngestControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * BOUNDARY — device ingress. Every device message lands here, is parsed, and is handed to
 * a control. No persistence happens in this class.
 *
 * <p>Topic shape: {@code greensense/{gardenId}/{deviceCode}/{kind}}. The {@code deviceCode}
 * is only trusted after {@link TelemetryIngestControl} resolves it to a sensor registered
 * in that garden — an unknown code is dropped, never auto-provisioned.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "greensense.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttTelemetryBoundary {

    private final TelemetryIngestControl ingestControl;
    private final ActuatorControl actuatorControl;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = MqttConfig.INBOUND_CHANNEL)
    public void handle(Message<?> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        String payload = String.valueOf(message.getPayload());

        if (topic == null) {
            log.warn("MQTT message without a topic header; dropped");
            return;
        }

        String[] parts = topic.split("/");
        if (parts.length < 4) {
            log.warn("Unexpected MQTT topic '{}'; dropped", topic);
            return;
        }

        String gardenId = parts[1];
        String deviceCode = parts[2];
        String kind = parts[3];

        try {
            switch (kind) {
                case "telemetry" -> handleTelemetry(gardenId, deviceCode, payload);
                case "status" -> handleStatus(gardenId, deviceCode, payload);
                case "ack" -> handleAck(payload);
                default -> log.debug("Ignoring MQTT topic kind '{}'", kind);
            }
        } catch (Exception ex) {
            // A malformed frame from one node must never stall the subscription.
            log.error("Failed to process MQTT message on {}: {}", topic, ex.getMessage());
        }
    }

    private void handleTelemetry(String gardenId, String deviceCode, String payload) throws Exception {
        DeviceDtos.TelemetryPayload frame =
                objectMapper.readValue(payload, DeviceDtos.TelemetryPayload.class);

        // A node may send one sample, or batch several channels into one publish.
        List<DeviceDtos.TelemetryPayload.Sample> samples = frame.samples();
        if (samples != null && !samples.isEmpty()) {
            for (DeviceDtos.TelemetryPayload.Sample sample : samples) {
                accept(gardenId, deviceCode, sample.channel(), sample.value(), sample.ts());
            }
            return;
        }
        accept(gardenId, deviceCode, frame.channel(), frame.value(), frame.ts());
    }

    private void accept(String gardenId, String deviceCode, String channel, Double value, Instant ts) {
        if (channel == null || value == null) {
            log.warn("Telemetry from {}/{} missing channel or value", gardenId, deviceCode);
            return;
        }
        ingestControl.ingest(gardenId, deviceCode, channel, value, ts);
    }

    private void handleStatus(String gardenId, String deviceCode, String payload) throws Exception {
        DeviceDtos.StatusPayload status = objectMapper.readValue(payload, DeviceDtos.StatusPayload.class);
        ingestControl.ingestStatus(gardenId, deviceCode, status.online(), status.battery(), status.fw());
    }

    private void handleAck(String payload) throws Exception {
        DeviceDtos.AckPayload ack = objectMapper.readValue(payload, DeviceDtos.AckPayload.class);
        if (ack.correlationId() == null) {
            log.warn("Ack without correlationId; dropped");
            return;
        }
        actuatorControl.applyAck(
                ack.correlationId(),
                "OK".equalsIgnoreCase(ack.status()),
                ack.state(),
                ack.error());
    }
}
