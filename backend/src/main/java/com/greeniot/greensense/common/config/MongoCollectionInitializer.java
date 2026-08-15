package com.greeniot.greensense.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;

/**
 * Creates {@code sensor_readings} as a time-series collection with a TTL.
 *
 * <p>{@code @TimeSeries} on the entity makes Spring Data create the collection lazily but
 * cannot express {@code expireAfterSeconds}, so the collection is created explicitly here
 * on first boot with a raw {@code create} command. Existing collections are left untouched.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoCollectionInitializer {

    private static final String READINGS = "sensor_readings";

    @Bean
    public ApplicationRunner initReadingsCollection(MongoTemplate mongoTemplate,
                                                    GreenSenseProperties properties) {
        return args -> {
            if (mongoTemplate.collectionExists(READINGS)) {
                log.debug("Time-series collection '{}' already present", READINGS);
                return;
            }

            long retentionSeconds = Duration.ofDays(properties.getReadings().getRetentionDays()).toSeconds();

            Document command = new Document("create", READINGS)
                    .append("timeseries", new Document("timeField", "timestamp")
                            .append("metaField", "meta")
                            .append("granularity", "minutes"))
                    .append("expireAfterSeconds", retentionSeconds);

            mongoTemplate.executeCommand(command);
            log.info("Created time-series collection '{}' with {}-day retention",
                    READINGS, properties.getReadings().getRetentionDays());
        };
    }
}
