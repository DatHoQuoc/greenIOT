package com.greeniot.greensense.control;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.greeniot.greensense.entity.SensorReading;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/** CONTROL — the "Xuất dữ liệu" button. Streams readings as CSV. */
@Service
@RequiredArgsConstructor
public class ExportControl {

    private static final String HEADER = "timestamp,sensorId,type,value,unit,quality";

    private final SensorReadingRepository readingRepository;

    /**
     * Writes CSV straight to the response stream, so a 30-day export never materialises
     * as one big String in memory.
     *
     * <p>A UTF-8 BOM is emitted first: without it Excel on Windows renders Vietnamese
     * column values as mojibake.
     */
    @Transactional(readOnly = true)
    public void writeCsv(String gardenId, SensorType type, Instant from, Instant to, OutputStream output)
            throws IOException {

        List<SensorReading> readings = readingRepository.findGardenSeries(gardenId, type, from, to);

        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write('﻿');
            writer.write(HEADER);
            writer.write('\n');

            for (SensorReading reading : readings) {
                writer.write(String.join(",",
                        reading.getTimestamp() == null ? "" : reading.getTimestamp().toString(),
                        reading.getMeta() == null ? "" : nullSafe(reading.getMeta().getSensorId()),
                        reading.getMeta() == null || reading.getMeta().getType() == null
                                ? "" : reading.getMeta().getType().name(),
                        reading.getValue() == null ? "" : reading.getValue().toString(),
                        nullSafe(reading.getUnit()),
                        reading.getQuality() == null ? "" : reading.getQuality().name()));
                writer.write('\n');
            }
            writer.flush();
        }
    }

    /**
     * Same window as {@link #writeCsv}, streamed as a JSON array.
     *
     * <p>Written by hand rather than serialising a {@code List}: an export is unbounded by
     * nature, and building the whole list in memory first is exactly how a 30-day export
     * of a chatty garden runs the heap out.
     */
    @Transactional(readOnly = true)
    public void writeJson(String gardenId, SensorType type, Instant from, Instant to, OutputStream output)
            throws IOException {

        List<SensorReading> readings = readingRepository.findGardenSeries(gardenId, type, from, to);

        try (JsonGenerator json = new JsonFactory().createGenerator(output, JsonEncoding.UTF8)) {
            json.writeStartArray();
            for (SensorReading reading : readings) {
                json.writeStartObject();
                json.writeStringField("timestamp",
                        reading.getTimestamp() == null ? null : reading.getTimestamp().toString());
                json.writeStringField("sensorId",
                        reading.getMeta() == null ? null : reading.getMeta().getSensorId());
                json.writeStringField("type",
                        reading.getMeta() == null || reading.getMeta().getType() == null
                                ? null : reading.getMeta().getType().name());
                if (reading.getValue() == null) {
                    json.writeNullField("value");
                } else {
                    json.writeNumberField("value", reading.getValue());
                }
                json.writeStringField("unit", reading.getUnit());
                json.writeStringField("quality",
                        reading.getQuality() == null ? null : reading.getQuality().name());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.flush();
        }
    }

    public String fileName(SensorType type, Instant from, Instant to, String extension) {
        return "greensense-%s-%s-%s.%s".formatted(
                type == null ? "all" : type.getSlug(),
                from.toString().substring(0, 10),
                to.toString().substring(0, 10),
                extension);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
