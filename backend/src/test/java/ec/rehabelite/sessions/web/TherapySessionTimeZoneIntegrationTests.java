package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ec.rehabelite.shared.domain.ClinicCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The date and time of a session are the local date and time of the clinic (Quito): they are stored exactly as the
 * client sends them, whatever the time zone of the JVM. Instants (timestamptz) keep the moment itself.
 * <p>
 * Every test runs with the JVM in UTC, like the CI runner and the production image, five hours ahead of Quito. The
 * database is read with plain JDBC and the values are compared as text or as an epoch, so no driver conversion
 * takes part in the check. With {@code hibernate.jdbc.time_zone} set to a zone other than the one of the JVM,
 * Hibernate shifted every {@code time} it wrote and read by the difference between both.
 */
@AutoConfigureMockMvc
class TherapySessionTimeZoneIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private TimeZone originalTimeZone;
    private User physio;
    private Long treatmentId;
    private LocalDate yesterday;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        physio = userRepository.save(User.create("zona-" + UUID.randomUUID() + "@rehabelite.test", "not-a-real-hash",
                "Lcda. Valeria Andrade", Role.PHYSIOTHERAPIST, null, Instant.now()));
        // Dates of Quito: between 19:00 and midnight there, the date of the JVM (UTC) is already the next day
        LocalDate today = ClinicCalendar.today(Clock.systemUTC());
        yesterday = today.minusDays(1);
        treatmentId = TestTreatments.insert(jdbc, "ACTIVE", today.minusDays(30));
    }

    @AfterEach
    void restoreTheTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    /** Times on both sides of midnight: a shift of five hours in either direction would also change the day. */
    @ParameterizedTest
    @ValueSource(strings = {"00:15", "08:00", "19:30", "23:45"})
    void aSessionIsStoredWithTheDateAndTimeSentAndTheInstantOfItsRegistration(String time) throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        String body = perform(post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(yesterday, time, 4, "COMPLETED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.sessionTime").value(time + ":00"))
                .andReturn().getResponse().getContentAsString();

        Instant after = Instant.now();
        Long sessionId = ((Number) JsonPath.read(body, "$.id")).longValue();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT session_date::text AS session_date, session_time::text AS session_time,
                       (EXTRACT(EPOCH FROM created_at) * 1000000)::bigint AS created_at_micros
                FROM therapy_sessions WHERE id = ?""", sessionId);
        assertThat(row).containsEntry("session_date", yesterday.toString())
                .containsEntry("session_time", time + ":00");
        assertThat(instantOf(row.get("created_at_micros"))).isBetween(before, after);
        assertThat(Instant.parse(JsonPath.<String>read(body, "$.createdAt"))).isBetween(before, after);

        // Envers writes the version with the same mapping
        assertThat(jdbc.queryForMap("""
                SELECT session_date::text AS session_date, session_time::text AS session_time
                FROM therapy_sessions_aud WHERE id = ?""", sessionId))
                .containsEntry("session_date", yesterday.toString())
                .containsEntry("session_time", time + ":00");

        // And the application reads back what the client sent
        perform(get("/api/therapy-sessions/{id}", sessionId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.sessionTime").value(time + ":00"));
    }

    /** What failed in CI before: a row written by SQL (a seed, a script) was read five hours later. */
    @Test
    void aSessionWrittenWithSqlIsReadWithItsDateTimeAndInstant() throws Exception {
        Long sessionId = jdbc.queryForObject("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date,
                                              session_time, session_type, activities_performed,
                                              clinical_observations, pain_score, progress_status, status,
                                              created_by, created_at)
                VALUES (?, ?, 1, ?::date, '08:00'::time, 'Fisioterapia lumbar mecánica', 'Movilización lumbar',
                        'Buena tolerancia al ejercicio', 5, 'IMPROVING', 'COMPLETED', ?,
                        '2026-03-10T23:30:00-05:00'::timestamptz)
                RETURNING id""", Long.class, treatmentId, physio.getId(), yesterday.toString(), physio.getId());

        perform(get("/api/therapy-sessions/{id}", sessionId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.sessionTime").value("08:00:00"))
                // 23:30 of March 10 in Quito is 04:30 of March 11 in UTC: the same instant
                .andExpect(jsonPath("$.createdAt").value("2026-03-11T04:30:00Z"));
    }

    private static Instant instantOf(Object epochMicros) {
        return Instant.EPOCH.plus(((Number) epochMicros).longValue(), ChronoUnit.MICROS);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(physio, Instant.now()).token()));
    }

}
