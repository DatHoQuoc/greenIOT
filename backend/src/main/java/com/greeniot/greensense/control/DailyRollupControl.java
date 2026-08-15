package com.greeniot.greensense.control;

import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.Sensor;
import com.greeniot.greensense.entity.SensorDailyStat;
import com.greeniot.greensense.repository.GardenRepository;
import com.greeniot.greensense.repository.SensorDailyStatRepository;
import com.greeniot.greensense.repository.SensorReadingRepository;
import com.greeniot.greensense.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROL — collapses yesterday's raw readings into one row per sensor.
 *
 * <p>Raw points expire after {@code greensense.readings.retention-days}; these rollups do
 * not. Without them a garden's history has a hard six-month horizon, which makes
 * season-over-season comparison impossible.
 *
 * <p>Runs at 00:20 local time, and is idempotent: re-running for a day overwrites that
 * day's row rather than duplicating it, so a missed night can simply be backfilled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRollupControl {

    private final GardenRepository gardenRepository;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final SensorDailyStatRepository dailyStatRepository;

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void rollUpYesterday() {
        int rows = 0;
        for (Garden garden : gardenRepository.findAll()) {
            LocalDate yesterday = LocalDate.now(zoneOf(garden)).minusDays(1);
            rows += rollUp(garden, yesterday);
        }
        log.info("Daily rollup wrote {} row(s)", rows);
    }

    /**
     * @return how many sensor rows were written for that day
     */
    @Transactional
    public int rollUp(Garden garden, LocalDate day) {
        ZoneId zone = zoneOf(garden);
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<Sensor> sensors = sensorRepository.findByGardenId(garden.getId());
        List<SensorDailyStat> batch = new ArrayList<>();

        for (Sensor sensor : sensors) {
            var readings = readingRepository
                    .findByMetaSensorIdAndTimestampBetweenOrderByTimestampAsc(sensor.getId(), from, to);
            if (readings.isEmpty()) {
                continue;
            }

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0;
            long count = 0;

            for (var reading : readings) {
                Double value = reading.getValue();
                if (value == null) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
                count++;
            }
            if (count == 0) {
                continue;
            }

            // Overwrite rather than insert: makes a backfill safe to run twice.
            SensorDailyStat stat = dailyStatRepository
                    .findBySensorIdAndDay(sensor.getId(), day)
                    .orElseGet(() -> SensorDailyStat.builder()
                            .sensorId(sensor.getId())
                            .day(day)
                            .build());

            stat.setGardenId(garden.getId());
            stat.setType(sensor.getType());
            stat.setUnit(sensor.getUnit());
            stat.setMin(round(min));
            stat.setMax(round(max));
            stat.setAvg(round(sum / count));
            stat.setSamples(count);
            batch.add(stat);
        }

        dailyStatRepository.saveAll(batch);
        return batch.size();
    }

    private static ZoneId zoneOf(Garden garden) {
        try {
            return ZoneId.of(garden.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
