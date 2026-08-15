package com.greeniot.greensense.repository;

import com.greeniot.greensense.entity.enums.SensorType;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SensorReadingRepositoryImpl implements SensorReadingRepositoryCustom {

    private static final String COLLECTION = "sensor_readings";

    private final MongoTemplate mongoTemplate;

    @Override
    public ReadingStats stats(String gardenId, SensorType type, Instant from, Instant to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(window(gardenId, type, from, to)),
                Aggregation.group()
                        .min("value").as("min")
                        .max("value").as("max")
                        .avg("value").as("avg")
                        .count().as("count"));

        Document result = mongoTemplate
                .aggregate(aggregation, COLLECTION, Document.class)
                .getUniqueMappedResult();

        if (result == null) {
            return null;
        }
        return new ReadingStats(
                doubleValue(result.get("min")),
                doubleValue(result.get("max")),
                doubleValue(result.get("avg")),
                longValue(result.get("count")));
    }

    @Override
    public List<BucketPoint> bucketedSeries(String gardenId, SensorType type,
                                            Instant from, Instant to, int bucketMinutes) {
        long bucketMillis = Math.max(1, bucketMinutes) * 60_000L;

        // Floor each timestamp onto the bucket grid: ts - (ts mod width).
        Document bucketStart = new Document("$toDate",
                new Document("$subtract", List.of(
                        new Document("$toLong", "$timestamp"),
                        new Document("$mod", List.of(new Document("$toLong", "$timestamp"), bucketMillis)))));

        List<Document> pipeline = List.of(
                new Document("$match", window(gardenId, type, from, to).getCriteriaObject()),
                new Document("$group", new Document("_id", bucketStart)
                        .append("avg", new Document("$avg", "$value"))
                        .append("min", new Document("$min", "$value"))
                        .append("max", new Document("$max", "$value"))
                        .append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("_id", 1)));

        return mongoTemplate.getCollection(COLLECTION)
                .aggregate(pipeline)
                .map(doc -> new BucketPoint(
                        ((Date) doc.get("_id")).toInstant(),
                        doubleValue(doc.get("avg")),
                        doubleValue(doc.get("min")),
                        doubleValue(doc.get("max")),
                        longValue(doc.get("count"))))
                .into(new java.util.ArrayList<>());
    }

    @Override
    public Map<String, LatestReading> latestPerSensor(String gardenId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("meta.gardenId").is(gardenId)),
                Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                Aggregation.group("meta.sensorId")
                        .first("meta.type").as("type")
                        .first("value").as("value")
                        .first("unit").as("unit")
                        .first("timestamp").as("timestamp"));

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);

        Map<String, LatestReading> latest = new LinkedHashMap<>();
        for (Document doc : results.getMappedResults()) {
            String sensorId = doc.getString("_id");
            if (sensorId == null) {
                continue;
            }
            Object rawTimestamp = doc.get("timestamp");
            latest.put(sensorId, new LatestReading(
                    sensorId,
                    doc.getString("type") == null ? null : SensorType.valueOf(doc.getString("type")),
                    doubleValue(doc.get("value")),
                    doc.getString("unit"),
                    rawTimestamp instanceof Date date ? date.toInstant() : null));
        }
        return latest;
    }

    private Criteria window(String gardenId, SensorType type, Instant from, Instant to) {
        Criteria criteria = Criteria.where("meta.gardenId").is(gardenId);
        if (type != null) {
            criteria = criteria.and("meta.type").is(type.name());
        }
        return criteria.and("timestamp").gte(from).lte(to);
    }

    private static double doubleValue(Object raw) {
        return raw instanceof Number number ? number.doubleValue() : 0d;
    }

    private static long longValue(Object raw) {
        return raw instanceof Number number ? number.longValue() : 0L;
    }
}
