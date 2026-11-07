package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-10: a correction never loses the previous content. Hibernate Envers keeps every version of the session in
 * therapy_sessions_aud, with its author and date in revinfo, and GET /versions returns them, most recent first.
 */
@AutoConfigureMockMvc
class TherapySessionVersionsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User physio;
    private Long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        physio = userRepository.save(User.create("versiones-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Lcda. Valeria Andrade", Role.PHYSIOTHERAPIST, null, Instant.now()));
        String body = perform(post("/api/treatments/{id}/therapy-sessions", TestTreatments.insertActive(jdbc))
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(6)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        sessionId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void aCorrectionKeepsThePreviousVersionInEnvers() throws Exception {
        perform(put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(1), 3, "Ejercicios de Williams", "")))
                .andExpect(status().isOk());

        // Insert (0) and update (1), both by the author, each with the content it had
        assertThat(jdbc.queryForList("""
                SELECT a.revtype, a.pain_score, a.activities_performed, a.sequence_number, a.responsible_id,
                       r.user_id, r.revision_timestamp IS NOT NULL AS timed
                FROM therapy_sessions_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = ? ORDER BY a.rev""", sessionId))
                .containsExactly(
                        Map.of("revtype", 0, "pain_score", 6, "activities_performed",
                                "Movilización lumbar y TENS 15 minutos", "sequence_number", 1,
                                "responsible_id", physio.getId(), "user_id", physio.getId(), "timed", true),
                        Map.of("revtype", 1, "pain_score", 3, "activities_performed", "Ejercicios de Williams",
                                "sequence_number", 1, "responsible_id", physio.getId(), "user_id", physio.getId(),
                                "timed", true));
    }

    @Test
    void theVersionsAreListedMostRecentFirstWithTheirAuthorAndDate() throws Exception {
        perform(put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(3)))
                .andExpect(status().isOk());
        User reader = userRepository.save(User.create("lector-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Lcdo. Mateo Ríos", Role.PHYSIOTHERAPIST, null, Instant.now()));

        // Any physiotherapist sees the versions, not only the author
        mockMvc.perform(get("/api/therapy-sessions/{id}/versions", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(reader, Instant.now()).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("UPDATE", "CREATE")))
                .andExpect(jsonPath("$[*].session.painScore", contains(3, 6)))
                .andExpect(jsonPath("$[*].session.sequenceNumber", contains(1, 1)))
                .andExpect(jsonPath("$[*].changedBy.id", contains(physio.getId().intValue(),
                        physio.getId().intValue())))
                .andExpect(jsonPath("$[*].changedBy.fullName", contains("Lcda. Valeria Andrade",
                        "Lcda. Valeria Andrade")))
                .andExpect(jsonPath("$[0].changedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].session.responsible.fullName").value("Lcda. Valeria Andrade"));
    }

    @Test
    void theVersionsOfAnUnknownSessionAnswer404() throws Exception {
        perform(get("/api/therapy-sessions/{id}/versions", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(physio, Instant.now()).token()));
    }

}
