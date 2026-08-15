package com.greeniot.greensense.boundary.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greeniot.greensense.boundary.dto.DeviceDtos;
import com.greeniot.greensense.common.config.GreenSenseProperties;
import com.greeniot.greensense.common.config.MqttConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * BOUNDARY — outbound device egress. Publishes a command to
 * {@code greensense/{gardenId}/{deviceCode}/command}.
 *
 * <p>The channel is optional so the API still boots with {@code greensense.mqtt.enabled=false};
 * in that mode publishing is logged and skipped rather than throwing.
 */
@Slf4j
@Component
public class MqttCommandPublisher {

    private final ObjectProvider<MessageChannel> outboundChannel;
    private final GreenSenseProperties properties;
    private final ObjectMapper objectMapper;

    public MqttCommandPublisher(
            @Qualifier(MqttConfig.OUTBOUND_CHANNEL) ObjectProvider<MessageChannel> outboundChannel,
            GreenSenseProperties properties,
            ObjectMapper objectMapper) {
        this.outboundChannel = outboundChannel;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** @return true when the payload was handed to the broker */
    public boolean publishCommand(String gardenId, String deviceCode, DeviceDtos.CommandPayload payload) {
        MessageChannel channel = outboundChannel.getIfAvailable();
        if (channel == null) {
            log.warn("MQTT disabled — command {} for {}/{} not published",
                    payload.command(), gardenId, deviceCode);
            return false;
        }

        String topic = "%s/%s/%s/command".formatted(
                properties.getMqtt().getTopicRoot(), gardenId, deviceCode);
        try {
            String json = objectMapper.writeValueAsString(payload);
            channel.send(MessageBuilder.withPayload(json)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build());
            log.debug("Published {} to {}", payload.command(), topic);
            return true;
        } catch (Exception ex) {
            log.error("Failed to publish command to {}: {}", topic, ex.getMessage());
            return false;
        }
    }
}
