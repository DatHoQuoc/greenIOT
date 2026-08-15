package com.greeniot.greensense.entity.enums;

import java.time.DayOfWeek;

public enum DayOfWeekCode {
    MON(DayOfWeek.MONDAY), TUE(DayOfWeek.TUESDAY), WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY), FRI(DayOfWeek.FRIDAY), SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek javaDay;

    DayOfWeekCode(DayOfWeek javaDay) {
        this.javaDay = javaDay;
    }

    public DayOfWeek toJavaDay() {
        return javaDay;
    }

    public static DayOfWeekCode from(DayOfWeek day) {
        for (DayOfWeekCode code : values()) {
            if (code.javaDay == day) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unmapped day: " + day);
    }
}
