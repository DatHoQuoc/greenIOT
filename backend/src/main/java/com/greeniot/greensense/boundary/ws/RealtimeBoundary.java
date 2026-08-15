package com.greeniot.greensense.boundary.ws;

import com.greeniot.greensense.boundary.dto.ActuatorDtos;
import com.greeniot.greensense.boundary.dto.AlertDtos;
import com.greeniot.greensense.boundary.dto.EventDtos;
import com.greeniot.greensense.boundary.dto.RealtimeDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * BOUNDARY — outbound WebSocket. Controls call this to push state to open browsers;
 * a failed push must never break the ingestion path, so every send is swallowed on error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBoundary {

    private static final String ROOT = "/topic/garden/";

    private final SimpMessagingTemplate messagingTemplate;

    public void pushReading(String gardenId, RealtimeDtos.ReadingPush payload) {
        send(gardenId, "reading", payload);
    }

    public void pushActuator(String gardenId, ActuatorDtos.ActuatorResponse actuator) {
        send(gardenId, "actuator", new RealtimeDtos.ActuatorPush(actuator));
    }

    public void pushAlert(String gardenId, AlertDtos.AlertResponse alert, long unreadCount) {
        send(gardenId, "alert", new RealtimeDtos.AlertPush(alert, unreadCount));
    }

    public void pushEvent(String gardenId, EventDtos.EventResponse event) {
        send(gardenId, "event", new RealtimeDtos.EventPush(event));
    }

    private void send(String gardenId, String channel, Object payload) {
        try {
            messagingTemplate.convertAndSend(ROOT + gardenId + "/" + channel, payload);
        } catch (RuntimeException ex) {
            log.warn("WebSocket push to garden {} channel {} failed: {}", gardenId, channel, ex.getMessage());
        }
    }
}
