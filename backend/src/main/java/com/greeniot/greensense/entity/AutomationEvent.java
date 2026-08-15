package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.EventCategory;
import com.greeniot.greensense.entity.enums.EventTone;
import com.greeniot.greensense.entity.enums.TriggerSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * ENTITY — one line of the "Lịch sử kích hoạt tự động" timeline.
 * Append-only; never updated after it is written.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "automation_events")
@CompoundIndex(name = "ix_event_timeline", def = "{'gardenId':1,'occurredAt':-1}")
public class AutomationEvent extends BaseDocument {

    @Indexed
    private String gardenId;

    private Instant occurredAt;

    private TriggerSource source;

    private EventCategory category;

    /** e.g. "Quạt tản nhiệt tự động bật". */
    private String title;

    /** e.g. "nhiệt độ vượt 30°C" — rendered as the small parenthetical line. */
    private String detail;

    @Builder.Default
    private EventTone tone = EventTone.GRAY;

    private String sensorId;

    private String actuatorId;

    private String ruleId;

    private String scheduleId;
}
