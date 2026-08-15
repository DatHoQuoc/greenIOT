package com.greeniot.greensense.entity.enums;

public enum ActuatorState {
    ON("Đang chạy"), OFF("Tắt"), OPEN("Mở"), CLOSED("Đóng"), ERROR("Lỗi");

    private final String label;

    ActuatorState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
