package com.greeniot.greensense.boundary.rest;

import com.greeniot.greensense.common.security.JwtService;
import com.greeniot.greensense.entity.Garden;
import com.greeniot.greensense.entity.User;
import com.greeniot.greensense.support.IntegrationTest;
import com.greeniot.greensense.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorisation matrix. These are the tests that would catch someone reading a
 * stranger's garden, or a household member quietly rewriting the automation rules.
 */
@IntegrationTest
class GardenAccessControlTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private JwtService jwtService;

    private User owner;
    private User member;
    private User stranger;
    private Garden garden;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
        owner = fixtures.user("owner@greensense.vn");
        member = fixtures.user("member@greensense.vn");
        stranger = fixtures.user("stranger@greensense.vn");
        garden = fixtures.garden(owner.getId());
    }

    @Test
    void anonymousCallerGetsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 404 rather than 403 on purpose: a 403 would confirm the id exists, letting an
     * attacker enumerate gardens they cannot read.
     */
    @Test
    void strangerCannotTellAnExistingGardenFromAMissingOne() throws Exception {
        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/dashboard").header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/gardens/000000000000000000000000/dashboard").header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void strangerSeesNoGardensInTheirList() throws Exception {
        mvc.perform(get("/api/v1/gardens").header("Authorization", bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void ownerCanShareAndTheMemberThenSeesTheGarden() throws Exception {
        addMember();

        mvc.perform(get("/api/v1/gardens").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                // The client uses this to hide owner-only controls rather than hit a 403.
                .andExpect(jsonPath("$.data[0].viewerIsOwner").value(false));

        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/dashboard").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
    }

    @Test
    void memberCanOperateTheGardenButNotReconfigureIt() throws Exception {
        addMember();

        // Operating: the emergency stop must work for anyone standing in the garden.
        mvc.perform(patch("/api/v1/gardens/" + garden.getId() + "/system")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}"""))
                .andExpect(status().isOk());

        // Reconfiguring: hardware registry is owner-only.
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/sensors")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"ESP32-B2","channel":"temp-9","type":"TEMPERATURE"}"""))
                .andExpect(status().isForbidden());

        // ...as are rules, schedules, thresholds and deletion.
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/rules")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","condition":{"sensorType":"TEMPERATURE","operator":"GT","value":30},
                                 "action":{"actuatorType":"FAN","command":"TURN_ON"}}"""))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/gardens/" + garden.getId() + "/thresholds")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thresholds":{}}"""))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/gardens/" + garden.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotInviteFurtherMembers() throws Exception {
        addMember();

        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/members")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"stranger@greensense.vn"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedMemberLosesAccessImmediately() throws Exception {
        addMember();

        mvc.perform(delete("/api/v1/gardens/" + garden.getId() + "/members/" + member.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/gardens/" + garden.getId() + "/dashboard").header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitingSomeoneWithoutAnAccountIsRejected() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@greensense.vn"}"""))
                .andExpect(status().isNotFound());
    }

    private void addMember() throws Exception {
        mvc.perform(post("/api/v1/gardens/" + garden.getId() + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@greensense.vn"}"""))
                .andExpect(status().isOk());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user.getId(), user.getEmail(), user.getRole().name());
    }
}
