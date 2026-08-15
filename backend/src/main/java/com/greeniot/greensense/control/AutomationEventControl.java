package com.greeniot.greensense.control;

import com.greeniot.greensense.boundary.dto.EventDtos;
import com.greeniot.greensense.boundary.ws.RealtimeBoundary;
import com.greeniot.greensense.entity.AutomationEvent;
import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import com.greeniot.greensense.repository.AutomationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** CONTROL — writes and reads the "Lịch sử kích hoạt tự động" timeline. */
@Service
@RequiredArgsConstructor
public class AutomationEventControl {

    private final AutomationEventRepository eventRepository;
    private final RealtimeBoundary realtimeBoundary;

    @Transactional
    public AutomationEvent record(AutomationEvent event) {
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        AutomationEvent saved = eventRepository.save(event);
        realtimeBoundary.pushEvent(saved.getGardenId(), EventDtos.EventResponse.from(saved));
        return saved;
    }

    /** Convenience overload for the common "something changed" entry. */
    @Transactional
    public AutomationEvent record(String gardenId, TriggerSource source, EventCategory category,
                                  String title, String detail, EventTone tone) {
        return record(AutomationEvent.builder()
                .gardenId(gardenId)
                .occurredAt(Instant.now())
                .source(source)
                .category(category)
                .title(title)
                .detail(detail)
                .tone(tone)
                .build());
    }

    @Transactional(readOnly = true)
    public List<EventDtos.EventResponse> timeline(String gardenId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return eventRepository.findByGardenIdOrderByOccurredAtDesc(gardenId, pageable)
                .map(EventDtos.EventResponse::from)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<EventDtos.EventResponse> timelineForSensor(String gardenId, String sensorId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return eventRepository.findByGardenIdAndSensorIdOrderByOccurredAtDesc(gardenId, sensorId, pageable)
                .map(EventDtos.EventResponse::from)
                .getContent();
    }
}
