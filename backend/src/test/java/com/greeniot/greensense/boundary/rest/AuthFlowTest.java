package com.greeniot.greensense.boundary.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the session lifecycle end to end, including the refresh-reuse defence. */
@IntegrationTest
class AuthFlowTest {

    private static final String COOKIE = "gs_refresh";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
    }

    @Test
    void registerReturnsAccessTokenInBodyAndRefreshOnlyAsHttpOnlyCookie() throws Exception {
        MvcResult result = register("owner@greensense.vn");

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.at("/data/accessToken").asText()).isNotBlank();
        // The refresh token must never be readable by page scripts.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");

        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("dup@greensense.vn");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@greensense.vn","password":"Green@123","fullName":"Again"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void wrongPasswordIs401AndLeaksNothing() throws Exception {
        register("owner@greensense.vn");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@greensense.vn","password":"WrongPass1"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid credentials"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheCookieAndMintsANewAccessToken() throws Exception {
        MvcResult registered = register("owner@greensense.vn");
        Cookie first = registered.getResponse().getCookie(COOKIE);

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn();

        Cookie second = refreshed.getResponse().getCookie(COOKIE);
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // The new access token still authenticates.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("owner@greensense.vn"));
    }

    @Test
    void reusingASpentRefreshTokenKillsTheWholeSession() throws Exception {
        MvcResult registered = register("owner@greensense.vn");
        Cookie first = registered.getResponse().getCookie(COOKIE);

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie(COOKIE);

        // Replaying the spent token is the signal that a copy escaped.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));

        // ...and the legitimate successor is revoked too, so the thief gains nothing.
        mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isConflict());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        MvcResult registered = register("owner@greensense.vn");
        Cookie cookie = registered.getResponse().getCookie(COOKIE);

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void changingPasswordEndsEveryOtherSession() throws Exception {
        MvcResult registered = register("owner@greensense.vn");
        String token = accessToken(registered);
        Cookie cookie = registered.getResponse().getCookie(COOKIE);

        mvc.perform(patch("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Green@123","newPassword":"Brand@New9"}"""))
                .andExpect(status().isOk());

        // A password changed *because it leaked* must not leave the thief's session alive.
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@greensense.vn","password":"Brand@New9"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void profileUpdatePersistsNotificationPreferences() throws Exception {
        String token = accessToken(register("owner@greensense.vn"));

        mvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Chủ vườn","notifyByEmail":true,
                                 "quietHoursStart":"22:00:00","quietHoursEnd":"06:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifyByEmail").value(true))
                .andExpect(jsonPath("$.data.quietHoursStart").value("22:00:00"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.fullName").value("Chủ vườn"));
    }

    @Test
    void pushTokenRegistrationIsIdempotent() throws Exception {
        String token = accessToken(register("owner@greensense.vn"));
        String body = """
                {"token":"fcm-device-1"}""";

        mvc.perform(post("/api/v1/auth/me/push-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mvc.perform(post("/api/v1/auth/me/push-tokens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushTokens.length()").value(1));
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Green@123","fullName":"Owner"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }
}
