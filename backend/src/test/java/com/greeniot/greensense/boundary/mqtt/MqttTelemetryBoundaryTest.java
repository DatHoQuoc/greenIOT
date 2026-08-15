package com.greeniot.greensense.boundary.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greeniot.greensense.control.ActuatorControl;
import com.greeniot.greensense.control.TelemetryIngestControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Frame parsing for the device ingress.
 *
 * <p>Plain unit test — no Spring, no broker. The point is that a device sending garbage
 * cannot stall the shared subscription for every other node, and that the topic is
 * decomposed correctly into garden / device / kind.
 */
class MqttTelemetryBoundaryTest {

    private TelemetryIngestControl ingestControl;
    private ActuatorControl actuatorControl;
    private MqttTelemetryBoundary boundary;

    @BeforeEach
    void setUp() {
        ingestControl = mock(TelemetryIngestControl.class);
        actuatorControl = mock(ActuatorControl.class);
        boundary = new MqttTelemetryBoundary(ingestControl, actuatorControl, new ObjectMapper()
                .findAndRegisterModules());
    }

    @Test
    void singleSampleTelemetryReachesIngestion() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/telemetry", """
                {"channel":"soil-1","value":24.3,"ts":"2026-08-12T07:00:00Z"}"""));

        verify(ingestControl).ingest("garden-1", "ESP32-A1", "soil-1", 24.3,
                Instant.parse("2026-08-12T07:00:00Z"));
    }

    /** A node that batches several probes into one publish must not lose any of them. */
    @Test
    void batchedSamplesAreAllIngested() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/telemetry", """
                {"samples":[
                  {"channel":"temp-1","value":28.4},
                  {"channel":"hum-1","value":65.0},
                  {"channel":"soil-1","value":41.2}
                ]}"""));

        verify(ingestControl).ingest("garden-1", "ESP32-A1", "temp-1", 28.4, null);
        verify(ingestControl).ingest("garden-1", "ESP32-A1", "hum-1", 65.0, null);
        verify(ingestControl).ingest("garden-1", "ESP32-A1", "soil-1", 41.2, null);
    }

    @Test
    void statusFrameUpdatesDeviceHealth() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/status", """
                {"online":true,"battery":87,"fw":"1.2.0"}"""));

        verify(ingestControl).ingestStatus("garden-1", "ESP32-A1", true, 87, "1.2.0");
    }

    @Test
    void ackFrameReconcilesTheCommand() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/ack", """
                {"correlationId":"abc-123","status":"OK","state":"ON"}"""));

        verify(actuatorControl).applyAck("abc-123", true, "ON", null);
    }

    @Test
    void failedAckIsPassedThroughAsFailure() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/ack", """
                {"correlationId":"abc-123","status":"ERROR","state":"OFF","error":"relay stuck"}"""));

        verify(actuatorControl).applyAck("abc-123", false, "OFF", "relay stuck");
    }

    @Test
    void malformedJsonIsSwallowedNotThrown() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/telemetry", "{not json"));
        verifyNoInteractions(ingestControl);
    }

    @Test
    void telemetryMissingChannelOrValueIsDropped() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/telemetry", """
                {"value":24.3}"""));
        boundary.handle(frame("greensense/garden-1/ESP32-A1/telemetry", """
                {"channel":"soil-1"}"""));

        verify(ingestControl, never()).ingest(anyString(), anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void unknownTopicShapesAreIgnored() {
        boundary.handle(frame("greensense/garden-1", "{}"));
        boundary.handle(frame("greensense/garden-1/ESP32-A1/unknown-kind", "{}"));

        verifyNoInteractions(ingestControl);
        verifyNoInteractions(actuatorControl);
    }

    @Test
    void messageWithoutATopicHeaderIsIgnored() {
        boundary.handle(MessageBuilder.withPayload("{}").build());
        verifyNoInteractions(ingestControl);
    }

    @Test
    void ackWithoutCorrelationIdIsIgnored() {
        boundary.handle(frame("greensense/garden-1/ESP32-A1/ack", """
                {"status":"OK"}"""));
        verify(actuatorControl, never()).applyAck(anyString(), eq(true), any(), any());
    }

    private Message<String> frame(String topic, String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.RECEIVED_TOPIC, topic)
                .build();
    }
}
