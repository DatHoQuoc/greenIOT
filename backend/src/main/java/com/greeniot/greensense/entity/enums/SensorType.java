package com.greeniot.greensense.entity.enums;

/** The five environment metrics the garden UI renders as tiles. */
public enum SensorType {
    TEMPERATURE("Nhiệt độ", "°C"),
    AIR_HUMIDITY("Độ ẩm không khí", "%"),
    SOIL_MOISTURE("Độ ẩm đất", "%"),
    LIGHT("Ánh sáng", "lux"),
    PH("Độ pH", "pH");

    private final String label;
    private final String defaultUnit;

    SensorType(String label, String defaultUnit) {
        this.label = label;
        this.defaultUnit = defaultUnit;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    /** Frontend route slug, e.g. {@code /sensor/soil-moisture}. */
    public String getSlug() {
        return name().toLowerCase().replace('_', '-');
    }

    public static SensorType fromSlug(String slug) {
        for (SensorType type : values()) {
            if (type.getSlug().equalsIgnoreCase(slug) || type.name().equalsIgnoreCase(slug)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown sensor type: " + slug);
    }
}
