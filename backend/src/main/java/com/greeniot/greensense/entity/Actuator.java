package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.ActuatorMode;
import com.greeniot.greensense.entity.enums.ActuatorState;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.TriggerSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

/** ENTITY — a controllable device: pump, curtain, fan, grow light. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "actuators")
@CompoundIndex(name = "ix_actuator_type", def = "{'gardenId':1,'type':1}")
public class Actuator extends BaseDocument {

    @Indexed
    private String gardenId;

    private String deviceCode;

    private String channel;

    private ActuatorType type;

    private String name;

    @Builder.Default
    private ActuatorState state = ActuatorState.OFF;

    @Builder.Default
    private ActuatorMode mode = ActuatorMode.AUTO;

    private Instant lastChangedAt;

    private TriggerSource lastChangedBy;

    /** Safety cap — the runtime sweep forces the device off past this. */
    @Builder.Default
    private Integer maxRuntimeMinutes = 30;

    /** Minimum rest between two activations; protects pump motors. */
    @Builder.Default
    private Integer cooldownMinutes = 5;

    /** Set when a command carries a duration; the sweep turns the device off at this instant. */
    private Instant autoOffAt;

    @Builder.Default
    private boolean enabled = true;

    public boolean isActive() {
        return state == ActuatorState.ON || state == ActuatorState.OPEN;
    }

    public boolean isInCooldown(Instant now) {
        if (lastChangedAt == null || cooldownMinutes == null || cooldownMinutes <= 0) {
            return false;
        }
        return lastChangedAt.plus(Duration.ofMinutes(cooldownMinutes)).isAfter(now);
    }
}
