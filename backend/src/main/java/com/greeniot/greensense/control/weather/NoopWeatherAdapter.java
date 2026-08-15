package com.greeniot.greensense.control.weather;

import com.greeniot.greensense.entity.Garden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback when no weather provider is enabled: rain is never "likely", so
 * {@code skipIfRainForecast} simply never fires and watering proceeds on schedule.
 *
 * <p>Having this bean rather than a null port means {@link com.greeniot.greensense.control.IrrigationScheduleControl}
 * has no branch for "is weather configured" — it always asks the port.
 */
@Component
@ConditionalOnMissingBean(OpenMeteoWeatherAdapter.class)
public class NoopWeatherAdapter implements WeatherPort {

    @Override
    public boolean isRainLikelySoon(Garden garden) {
        return false;
    }
}
