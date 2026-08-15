package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.common.security.JwtService;
import com.greeniot.greensense.entity.Actuator;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.entity.enums.ActuatorType;
import com.greeniot.greensense.entity.enums.SensorType;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Một request hỏng phải trả <b>400</b> kèm lý do, không phải 500.
 *
 * <p>Cả class này sinh ra từ một đợt chạy thật lên production: bốn tình huống dưới đây
 * đều trả 500 với thông báo "Unexpected error", khiến người gọi tưởng server hỏng trong
 * khi lỗi nằm ở request của họ. Nguyên nhân là {@code GlobalExceptionHandler} thiếu
 * handler cho {@code HttpMessageNotReadableException}, và {@code IngestBatchRequest}
 * thiếu {@code @Valid} nên validation không đi vào từng phần tử của danh sách.
 */
@IntegrationTest
class RequestValidationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private JwtService jwtService;

    private String token;
    private Garden garden;
    private Actuator pump;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
        User owner = fixtures.user("owner@greensense.vn");
        token = "Bearer " + jwtService.issue(owner.getId(), owner.getEmail(), owner.getRole().name());
        garden = fixtures.garden(owner.getId());
        fixtures.sensor(garden.getId(), "temp-1", SensorType.TEMPERATURE);
        pump = fixtures.actuator(garden.getId(), "pump-1", ActuatorType.WATER_PUMP);
    }

    /** Hằng số enum không tồn tại. Jackson biết danh sách hợp lệ — phải nói ra cho người gọi. */
    @Test
    void unknownEnumConstantIs400WithTheAllowedValues() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/actuators/" + pump.getId() + "/command")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"command":"EXPLODE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.error.details.field").value("command"))
                .andExpect(jsonPath("$.error.details.allowed").value(
                        org.hamcrest.Matchers.containsString("TURN_ON")));
    }

    /**
     * Thiếu {@code value} trong một phần tử của mảng {@code readings}.
     *
     * <p>Thiếu {@code @Valid} trên danh sách thì bean validation dừng ở mức "danh sách khác
     * null", giá trị null lọt xuống tầng dưới rồi nổ NPE — 500 thay vì 400.
     */
    @Test
    void missingFieldInsideAListElementIs400NotAnNpe() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/readings/ingest")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"readings":[{"deviceCode":"ESP32-A1","channel":"temp-1"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").exists());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/readings/ingest")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    /**
     * Đây là tình huống thật đã gặp: client gửi tiếng Việt bằng CP1252 thay vì UTF-8
     * ({@code 0xB0} là ký tự {@code °} trong CP1252, còn UTF-8 phải là {@code 0xC2 0xB0}).
     * Server từ chối là đúng — nhưng phải trả 400, không phải 500.
     */
    @Test
    void invalidUtf8InTheBodyIs400() throws Exception {
        byte[] cp1252 = new byte[]{
                '{', '"', 'p', 'h', '"', ':', ' ', '"', (byte) 0xB0, 'C', '"', '}'
        };

        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/soil/analyze")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cp1252))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    /** Tiếng Việt gửi đúng UTF-8 thì phải đi qua trọn vẹn, không rơi rụng dấu. */
    @Test
    void properUtf8VietnameseRoundTripsIntact() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/actuators")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"ESP32-A1","channel":"curtain-1","type":"CURTAIN",
                                 "name":"Rèm che nắng"}""".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Rèm che nắng"));
    }

    @Test
    void missingRequiredQueryParameterIs400() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/series?range=24H")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.error.details.parameter").value("type"));
    }

    @Test
    void unknownSensorTypeSlugIs400() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/series?type=radiation&range=24H")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void unsupportedRangeIs400() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/readings/series?type=temperature&range=99Y")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }
}
