package com.greeniot.greensense.common.config;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

/**
 * MQTT plumbing. Devices publish telemetry/status/ack; the server publishes commands.
 * Disable with {@code greensense.mqtt.enabled=false} to run the API without a broker.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "greensense.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    public static final String INBOUND_CHANNEL = "mqttInboundChannel";
    public static final String OUTBOUND_CHANNEL = "mqttOutboundChannel";

    private final GreenSenseProperties properties;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getMqtt().getBrokerUrl()});
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        if (StringUtils.hasText(properties.getMqtt().getUsername())) {
            options.setUserName(properties.getMqtt().getUsername());
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(name = INBOUND_CHANNEL)
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean(name = OUTBOUND_CHANNEL)
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * Subscribes to every device topic in one adapter. The concrete topic arrives as the
     * {@code mqtt_receivedTopic} header, which the boundary parses back into
     * {@code gardenId / deviceCode / kind}.
     */
    @Bean
    public MessageProducer mqttInbound() {
        String root = properties.getMqtt().getTopicRoot();
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                properties.getMqtt().getClientId() + "-in",
                mqttClientFactory(),
                root + "/+/+/telemetry",
                root + "/+/+/status",
                root + "/+/+/ack");

        adapter.setCompletionTimeout(5_000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(properties.getMqtt().getQos());
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = OUTBOUND_CHANNEL)
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                properties.getMqtt().getClientId() + "-out", mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultQos(properties.getMqtt().getQos());
        handler.setDefaultRetained(false);
        return handler;
    }
}
