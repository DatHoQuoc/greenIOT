package com.greeniot.greensense.entity.enums;

/** Who caused a state change — drives the timeline copy and the audit trail. */
public enum TriggerSource {
    SYSTEM, USER, RULE, SCHEDULE, DEVICE
}
