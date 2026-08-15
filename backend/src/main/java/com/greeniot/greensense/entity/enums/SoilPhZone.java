package com.greeniot.greensense.entity.enums;

/** pH bands mirroring the reference scale drawn on the soil-analysis screen. */
public enum SoilPhZone {
    STRONGLY_ACIDIC("Đất chua nhiều"),
    SLIGHTLY_ACIDIC("Đất chua nhẹ"),
    NEUTRAL("Đất trung tính"),
    ALKALINE("Đất kiềm");

    private final String label;

    SoilPhZone(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
