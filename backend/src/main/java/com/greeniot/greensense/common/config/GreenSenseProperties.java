package com.greeniot.greensense.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "greensense")
public class GreenSenseProperties {

    private Mqtt mqtt = new Mqtt();
    private Readings readings = new Readings();
    private Automation automation = new Automation();
    private Weather weather = new Weather();

    @Getter
    @Setter
    public static class Mqtt {
        private boolean enabled = true;
        private String brokerUrl = "tcp://localhost:1883";
        private String clientId = "greensense-server";
        private String username = "";
        private String password = "";
        private String topicRoot = "greensense";
        private int qos = 1;
    }

    @Getter
    @Setter
    public static class Readings {
        private int retentionDays = 180;
        /** A sensor silent for interval x factor is flipped to OFFLINE. */
        private int offlineFactor = 3;
    }

    @Getter
    @Setter
    public static class Automation {
        private int alertDedupeMinutes = 30;
        private int commandTimeoutSeconds = 30;
    }

    @Getter
    @Setter
    public static class Weather {
        /** Off by default: a boxed install makes no outbound calls unless asked. */
        private boolean enabled = false;
        /** How far ahead to look for rain when deciding to skip a watering. */
        private int lookaheadHours = 6;
        /** Percent chance at or above which watering is skipped. */
        private int rainProbabilityThreshold = 60;
        /** The irrigation ticker runs every minute; the outlook does not change that fast. */
        private int cacheMinutes = 30;
    }
}
