package com.greeniot.greensense.entity.enums;

public enum RuleOperator {
    GT, GTE, LT, LTE, BETWEEN, OUTSIDE;

    /**
     * @param value      the measured value
     * @param threshold  primary comparison value
     * @param secondary  upper bound, only used by BETWEEN / OUTSIDE
     */
    public boolean test(double value, double threshold, Double secondary) {
        return switch (this) {
            case GT -> value > threshold;
            case GTE -> value >= threshold;
            case LT -> value < threshold;
            case LTE -> value <= threshold;
            case BETWEEN -> secondary != null && value >= threshold && value <= secondary;
            case OUTSIDE -> secondary != null && (value < threshold || value > secondary);
        };
    }
}
