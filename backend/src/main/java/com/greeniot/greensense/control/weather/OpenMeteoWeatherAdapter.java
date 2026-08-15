package com.greeniot.greensense.control.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.greeniot.greensense.common.config.GreenSenseProperties;
import com.greeniot.greensense.entity.Garden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rain outlook from Open-Meteo — free, no API key, no account.
 *
 * <p>Results are cached per garden for {@code cache-minutes}: the irrigation ticker runs
 * every minute, and a garden's rain outlook does not change that fast. Without the cache a
 * handful of schedules would hammer a public endpoint 1 440 times a day each.
 *
 * <p>Enable with {@code greensense.weather.enabled=true}. Off by default so a boxed
 * install never makes outbound calls the operator did not ask for.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "greensense.weather", name = "enabled", havingValue = "true")
public class OpenMeteoWeatherAdapter implements WeatherPort {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;
    private final GreenSenseProperties properties;
    private final Map<String, CachedOutlook> cache = new ConcurrentHashMap<>();

    public OpenMeteoWeatherAdapter(RestClient.Builder builder, GreenSenseProperties properties) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.properties = properties;
    }

    @Override
    public boolean isRainLikelySoon(Garden garden) {
        if (garden.getLocation() == null
                || garden.getLocation().getLatitude() == null
                || garden.getLocation().getLongitude() == null) {
            log.debug("Garden {} has no coordinates; rain check skipped", garden.getId());
            return false;
        }

        CachedOutlook cached = cache.get(garden.getId());
        long ttlMillis = Duration.ofMinutes(properties.getWeather().getCacheMinutes()).toMillis();
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis() < ttlMillis) {
            return cached.rainLikely();
        }

        boolean rainLikely = fetchRainLikely(garden);
        cache.put(garden.getId(), new CachedOutlook(rainLikely, System.currentTimeMillis()));
        return rainLikely;
    }

    private boolean fetchRainLikely(Garden garden) {
        try {
            ForecastResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", garden.getLocation().getLatitude())
                            .queryParam("longitude", garden.getLocation().getLongitude())
                            .queryParam("hourly", "precipitation_probability")
                            .queryParam("forecast_hours", properties.getWeather().getLookaheadHours())
                            .queryParam("timezone", "UTC")
                            .build())
                    .retrieve()
                    .body(ForecastResponse.class);

            if (response == null || response.hourly() == null
                    || response.hourly().precipitationProbability() == null) {
                return false;
            }

            int threshold = properties.getWeather().getRainProbabilityThreshold();
            return response.hourly().precipitationProbability().stream()
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(probability -> probability >= threshold);

        } catch (RuntimeException ex) {
            // Provider down must not cancel watering — fail open, not closed.
            log.warn("Open-Meteo lookup failed for garden {}: {}", garden.getId(), ex.getMessage());
            return false;
        }
    }

    private record CachedOutlook(boolean rainLikely, long fetchedAtMillis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastResponse(Hourly hourly) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Hourly(
                @com.fasterxml.jackson.annotation.JsonProperty("precipitation_probability")
                List<Integer> precipitationProbability) {
        }
    }
}
