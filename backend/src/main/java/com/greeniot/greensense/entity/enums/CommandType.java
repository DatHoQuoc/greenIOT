package com.greeniot.greensense.entity.enums;

public enum CommandType {
    TURN_ON, TURN_OFF, OPEN, CLOSE;

    public ActuatorState resultingState() {
        return switch (this) {
            case TURN_ON -> ActuatorState.ON;
            case TURN_OFF -> ActuatorState.OFF;
            case OPEN -> ActuatorState.OPEN;
            case CLOSE -> ActuatorState.CLOSED;
        };
    }
}
