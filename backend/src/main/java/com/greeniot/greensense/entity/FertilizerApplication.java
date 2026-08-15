package com.greeniot.greensense.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * ENTITY — the "Đánh dấu đã bón phân hôm nay" log.
 * The unique (gardenId, appliedOn) index makes marking today idempotent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "fertilizer_applications")
@CompoundIndex(name = "uk_fertilizer_day", def = "{'gardenId':1,'appliedOn':1}", unique = true)
public class FertilizerApplication extends BaseDocument {

    @Indexed
    private String gardenId;

    private String userId;

    /** Calendar day in the garden's timezone. */
    private LocalDate appliedOn;

    private String fertilizerName;

    private String dosage;

    private String note;

    private String soilAnalysisId;
}
