package com.greeniot.greensense.control.weather;

import com.greeniot.greensense.entity.Garden;

/**
 * Rain outlook for a garden, used by the {@code skipIfRainForecast} switch on an
 * irrigation schedule.
 *
 * <p>A port rather than a direct HTTP call so the scheduler stays testable offline and so
 * swapping providers does not touch irrigation logic.
 */
public interface WeatherPort {

    /**
     * @return true when rain is likely enough in the next few hours that watering now
     *         would be wasted. Any uncertainty — no coordinates, provider down, provider
     *         disabled — must answer {@code false}: skipping a watering on a guess is
     *         worse than one redundant cycle.
     */
    boolean isRainLikelySoon(Garden garden);
}
