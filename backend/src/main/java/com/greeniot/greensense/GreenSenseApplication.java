package com.greeniot.greensense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GreenSense — smart garden IoT backend.
 *
 * <p>Layering follows the BCE (Boundary-Control-Entity) robustness model:
 * {@code boundary} holds everything the outside world talks to (REST, MQTT, WebSocket),
 * {@code control} holds use-case logic, {@code entity} holds the persistent domain model.
 * Dependencies only ever point boundary → control → repository → entity.
 */
@SpringBootApplication
@EnableMongoAuditing
@EnableScheduling
@EnableAsync
public class GreenSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenSenseApplication.class, args);
    }
}
