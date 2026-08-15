package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.common.security.JwtService;
import com.greeniot.greensense.control.TelemetryIngestControl;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON field names the frontend's TypeScript types are written against.
 *
 * <p>The two sides are separate builds; nothing but this test stops a rename here from
 * silently turning a value into `undefined` on a phone screen. Every assertion below
 * corresponds to a field in {@code frontend/lib/api/types.ts}.
 */
@IntegrationTest
class DashboardContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private JwtService jwtService;
    @Autowired private TelemetryIngestControl ingestControl;

    private String token;
    private Garden garden;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
        User owner = fixtures.user("owner@greensense.vn");
        token = "Bearer " + jwtService.issue(owner.getId(), owner.getEmail(), owner.getRole().name());

        garden = fixtures.garden(owner.getId());
        fixtures.sensor(garden.getId(), "temp-1", SensorType.TEMPERATURE);
        fixtures.sensor(garden.getId(), "soil-1", SensorType.SOIL_MOISTURE);
        fixtures.sensor(garden.getId(), "ph-1", SensorType.PH);
        fixtures.actuator(garden.getId(), "pump-1", ActuatorType.WATER_PUMP);

        ingestControl.ingest(garden.getId(), "ESP32-A1", "temp-1", 27.4, null);
        ingestControl.ingest(garden.getId(), "ESP32-A1", "soil-1", 44.0, null);
        ingestControl.ingest(garden.getId(), "ESP32-A1", "ph-1", 6.2, null);
    }

    @Test
    void dashboardMatchesTheFrontendDashboardType() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/dashboard").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // garden
                .andExpect(jsonPath("$.data.garden.id").exists())
                .andExpect(jsonPath("$.data.garden.name").value("Vườn Nhà"))
                .andExpect(jsonPath("$.data.garden.systemEnabled").value(true))
                .andExpect(jsonPath("$.data.garden.viewerIsOwner").value(true))
                .andExpect(jsonPath("$.data.garden.members").isArray())
                .andExpect(jsonPath("$.data.garden.thresholds.TEMPERATURE.warnHigh").value(30.0))
                // sensor tiles — the home grid
                .andExpect(jsonPath("$.data.sensors").isArray())
                .andExpect(jsonPath("$.data.sensors[0].sensorId").exists())
                .andExpect(jsonPath("$.data.sensors[0].slug").exists())
                .andExpect(jsonPath("$.data.sensors[0].label").exists())
                .andExpect(jsonPath("$.data.sensors[0].breached").exists())
                // actuator pills
                .andExpect(jsonPath("$.data.actuators[0].stateLabel").exists())
                .andExpect(jsonPath("$.data.actuators[0].typeLabel").exists())
                // hero counters + badge
                .andExpect(jsonPath("$.data.sensorCount").value(3))
                .andExpect(jsonPath("$.data.pumpCount").value(1))
                .andExpect(jsonPath("$.data.unreadAlerts").exists())
                .andExpect(jsonPath("$.data.live").value(true))
                .andExpect(jsonPath("$.data.recentEvents").isArray())
                // soil card, derived from the pH reading above
                .andExpect(jsonPath("$.data.latestSoil.ph").value(6.2))
                .andExpect(jsonPath("$.data.latestSoil.zoneLabel").value("Đất chua nhẹ"))
                .andExpect(jsonPath("$.data.latestSoil.recommendation.title")
                        .value("Phân NPK 16-16-8 + Vôi bột"))
                .andExpect(jsonPath("$.data.latestSoil.recommendation.dosage")
                        .value("200g vôi/m² + 50g NPK/m²"))
                .andExpect(jsonPath("$.data.latestSoil.recommendation.alternatives").isArray());
    }

    @Test
    void summaryMatchesTheFrontendSummaryType() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/summary?type=temperature&range=24H")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TEMPERATURE"))
                .andExpect(jsonPath("$.data.unit").value("°C"))
                .andExpect(jsonPath("$.data.current").value(27.4))
                .andExpect(jsonPath("$.data.min").exists())
                .andExpect(jsonPath("$.data.max").exists())
                .andExpect(jsonPath("$.data.samples").exists())
                // `trend` drives the "↑ Tăng…" copy; it must always be present, never null.
                .andExpect(jsonPath("$.data.trend").exists())
                .andExpect(jsonPath("$.data.threshold.warnHigh").value(30.0));
    }

    @Test
    void seriesMatchesTheFrontendSeriesType() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/series?type=temperature&range=24H")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TEMPERATURE"))
                .andExpect(jsonPath("$.data.bucketMinutes").value(60))
                .andExpect(jsonPath("$.data.points").isArray())
                .andExpect(jsonPath("$.data.points[0].timestamp").exists())
                .andExpect(jsonPath("$.data.points[0].value").exists())
                .andExpect(jsonPath("$.data.points[0].samples").exists());
    }

    /** The slug form is what the app routes by, so BE must accept it as well as the enum. */
    @Test
    void sensorTypeSlugsAreAccepted() throws Exception {
        for (String slug : new String[]{"temperature", "air-humidity", "soil-moisture", "light", "ph"}) {
            mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/series?type=" + slug + "&range=24H")
                            .header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void phScaleMatchesTheFrontendReferenceType() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/soil/ph-scale").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].zone").exists())
                .andExpect(jsonPath("$.data[0].label").exists())
                .andExpect(jsonPath("$.data[0].from").exists())
                .andExpect(jsonPath("$.data[0].to").exists())
                .andExpect(jsonPath("$.data[0].suitableFor").exists());
    }

    @Test
    void alertPageMatchesTheFrontendPageType() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/alerts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalItems").exists())
                .andExpect(jsonPath("$.data.totalPages").exists())
                .andExpect(jsonPath("$.data.last").exists());
    }

    @Test
    void unreadCountIsAnObjectNotABareNumber() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/alerts/unread-count")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").exists());
    }
}
