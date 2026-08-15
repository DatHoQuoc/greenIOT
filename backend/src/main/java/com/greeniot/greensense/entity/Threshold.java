package com.greeniot.greensense.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embedded value object: the acceptable band for one metric in one garden.
 *
 * <p>{@code warnLow}/{@code warnHigh} are the dashed lines the charts draw
 * ("Ngưỡng cảnh báo 30°C"); {@code min}/{@code max} are the hard bounds beyond
 * which a CRITICAL alert is raised.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Threshold {

    private Double min;
    private Double max;
    private Double warnLow;
    private Double warnHigh;
    private String unit;

    public boolean isBelowWarn(double value) {
        return warnLow != null && value < warnLow;
    }

    public boolean isAboveWarn(double value) {
        return warnHigh != null && value > warnHigh;
    }

    public boolean isCritical(double value) {
        return (min != null && value < min) || (max != null && value > max);
    }

    public boolean isBreached(double value) {
        return isBelowWarn(value) || isAboveWarn(value) || isCritical(value);
    }
}
