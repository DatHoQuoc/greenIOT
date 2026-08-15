package com.greeniot.greensense.entity.enums;

public enum ActuatorType {
    WATER_PUMP("Bơm nước"),
    CURTAIN("Rèm"),
    FAN("Quạt"),
    GROW_LIGHT("Đèn"),
    VALVE("Van");

    private final String label;

    ActuatorType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
