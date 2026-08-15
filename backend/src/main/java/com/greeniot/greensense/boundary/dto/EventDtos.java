package com.greeniot.greensense.boundary.dto;

import com.greeniot.greensense.entity.AutomationEvent;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.TriggerSource;

import java.time.Instant;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventResponse(
            String id,
            Instant occurredAt,
            TriggerSource source,
            EventCategory category,
            String title,
            String detail,
            EventTone tone,
            String sensorId,
            String actuatorId,
            String ruleId,
            String scheduleId) {

        public static EventResponse from(AutomationEvent event) {
            return new EventResponse(
                    event.getId(),
                    event.getOccurredAt(),
                    event.getSource(),
                    event.getCategory(),
                    event.getTitle(),
                    event.getDetail(),
                    event.getTone(),
                    event.getSensorId(),
                    event.getActuatorId(),
                    event.getRuleId(),
                    event.getScheduleId());
        }
    }
}
