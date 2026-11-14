package ec.rehabelite.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * HU-13: in the portal a patient reads the sessions of the own treatment and the last instructions of the
 * physiotherapist, never the clinical observations nor the progress status, which are internal.
 * <p>
 * The active treatment of Rosa started 30 days ago. Its sessions were registered out of chronological order:
 * <pre>
 *  #1  day 2  10:00  COMPLETED    Valeria  EVA 6  "Calor local 15 minutos"
 *  #2  day 5  08:00  RESCHEDULED  Mateo    EVA 6  no instructions
 *  #3  day 2  08:00  COMPLETED    Mateo    EVA 5  "Caminata suave diaria"
 *  #4  day 9  09:00  NO_SHOW      Mateo    EVA 5  "Reagendar la sesión esta semana"
 *  #5  day 9  08:00  COMPLETED    Valeria  EVA 3  no instructions
 * </pre>
 * Every session has the internal observation "Observación clínica interna" and the progress status IMPROVING. Rosa
 * also has a finished treatment whose only session has no instructions; Luis is another patient.
 */
@AutoConfigureMockMvc
class PortalSessionsIntegrationTests extends AbstractIntegrationTest {

    private static final String NOT_ACCESSIBLE = "No tiene acceso a ese tratamiento.";
    private static final String NO_LINKED_PATIENT = "La cuenta no tiene un paciente vinculado.";
    /** The properties of a session in the portal: nothing else may ever appear. */
    private static final Set<String> SESSION_PROPERTIES = Set.of("sequenceNumber", "sessionDate", "sessionTime",
            "sessionType", "responsibleName", "painScore", "activitiesPerformed", "status", "patientInstructions");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User rosa;
    private Long activeTreatment;
    private Long finishedTreatment;
    private Long treatmentOfLuis;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        User valeria = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        User mateo = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        LocalDate today = LocalDate.now();
        start = today.minusDays(30);

        rosa = createUser(Role.PATIENT, "Rosa Elena Ortiz Vega");
        Long record = TestTreatments.insertRecord(jdbc, insertLinkedPatient(rosa, "Rosa Elena", "Ortiz Vega"));
        finishedTreatment = TestTreatments.insertFor(jdbc, record, "COMPLETED", today.minusDays(200), 8, 10);
        TestSessions.insert(jdbc, finishedTreatment, mateo.getId(), 1, today.minusDays(190), "09:00", 2, "COMPLETED",
                null);
        activeTreatment = TestTreatments.insertFor(jdbc, record, "ACTIVE", start, 7, 12);
        session(valeria, 1, 2, "10:00", 6, "COMPLETED", "Calor local 15 minutos");
        session(mateo, 2, 5, "08:00", 6, "RESCHEDULED", null);
        session(mateo, 3, 2, "08:00", 5, "COMPLETED", "Caminata suave diaria");
        session(mateo, 4, 9, "09:00", 5, "NO_SHOW", "Reagendar la sesión esta semana");
        session(valeria, 5, 9, "08:00", 3, "COMPLETED", null);

        User luis = createUser(Role.PATIENT, "Luis Alberto Quishpe Tipán");
        treatmentOfLuis = TestTreatments.insertFor(jdbc,
                TestTreatments.insertRecord(jdbc, insertLinkedPatient(luis, "Luis Alberto", "Quishpe Tipán")),
                "ACTIVE", today.minusDays(10), 9, 6);
        TestSessions.insert(jdbc, treatmentOfLuis, valeria.getId(), 1, today.minusDays(5), "09:00", 8, "COMPLETED",
                "Indicación de otro paciente");
    }

    // ----- GET /api/portal/treatments/{id}/sessions -----

    @Test
    void theSessionsComeInChronologicalOrderWhateverTheirStatus() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                // Date, then time: sessions 3 and 1 are of day 2, sessions 5 and 4 of day 9
                .andExpect(jsonPath("$[*].sequenceNumber", contains(3, 1, 2, 5, 4)))
                .andExpect(jsonPath("$[*].sessionDate", contains(day(2), day(2), day(5), day(9), day(9))))
                .andExpect(jsonPath("$[*].sessionTime",
                        contains("08:00:00", "10:00:00", "08:00:00", "08:00:00", "09:00:00")))
                .andExpect(jsonPath("$[*].status",
                        contains("COMPLETED", "COMPLETED", "RESCHEDULED", "COMPLETED", "NO_SHOW")));
    }

    @Test
    void eachSessionHasItsTypePhysiotherapistPainScoreActivitiesAndInstructions() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionType").value("Fisioterapia lumbar mecánica"))
                .andExpect(jsonPath("$[0].activitiesPerformed").value("Movilización lumbar"))
                .andExpect(jsonPath("$[*].responsibleName", contains("Lcdo. Mateo Ríos", "Lcda. Valeria Andrade",
                        "Lcdo. Mateo Ríos", "Lcda. Valeria Andrade", "Lcdo. Mateo Ríos")))
                .andExpect(jsonPath("$[*].painScore", contains(5, 6, 6, 3, 5)))
                .andExpect(jsonPath("$[*].patientInstructions", contains("Caminata suave diaria",
                        "Calor local 15 minutos", null, null, "Reagendar la sesión esta semana")));
    }

    @Test
    void theJsonOfTheSessionsHasNoClinicalObservationsNorProgressStatus() throws Exception {
        String body = perform(rosa, get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Neither the properties nor their values, which every session of the test has
        assertThat(body).doesNotContain("clinicalObservations", "Observación clínica interna", "progressStatus",
                "IMPROVING", "observ", "progress");
        assertThat(body).doesNotContain("patientId", "responsibleId", "treatmentId", "createdBy", "updatedBy");
        List<Map<String, Object>> sessions = JsonPath.read(body, "$");
        assertThat(sessions).hasSize(5)
                .allSatisfy(session -> assertThat(session.keySet()).isEqualTo(SESSION_PROPERTIES));
    }

    @Test
    void theSessionsInTheDatabaseDoHaveTheFieldsThePortalLeavesOut() {
        // Guards the test above: the data it reads really has observations and a progress status
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT clinical_observations, progress_status FROM therapy_sessions
                WHERE treatment_id = ?""", activeTreatment))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("clinical_observations", "Observación clínica interna")
                        .containsEntry("progress_status", "IMPROVING"));
    }

    @Test
    void aTreatmentWithoutSessionsHasAnEmptyList() throws Exception {
        Long withoutSessions = jdbc.queryForObject("""
                INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness,
                                        referring_physician, complementary_tests, severity, pain_type, pain_location,
                                        initial_pain_score, pain_frequency, session_frequency, goal, planned_sessions,
                                        status, start_date)
                SELECT clinical_record_id, diagnosis_code, chief_complaint, current_illness, referring_physician,
                       complementary_tests, severity, pain_type, pain_location, initial_pain_score, pain_frequency,
                       session_frequency, goal, planned_sessions, 'ABANDONED', start_date - 60
                FROM treatments WHERE id = ?
                RETURNING id""", Long.class, activeTreatment);

        perform(rosa, get("/api/portal/treatments/{id}/sessions", withoutSessions))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void readingTheSessionsIsAuditedAsReadOfTheTreatment() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/sessions", activeTreatment)).andExpect(status().isOk());

        assertThat(entriesOf(rosa)).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Treatment")
                .containsEntry("entity_id", activeTreatment)
                .containsEntry("ip_address", "127.0.0.1"));
    }

    // ----- GET /api/portal/treatments/{id}/instructions -----

    @Test
    void theInstructionsAreThoseOfTheMostRecentSessionThatHasAnyWhateverItsStatus() throws Exception {
        // Session 5 is of the same day but earlier and has none; session 4, a no-show, is the most recent
        perform(rosa, get("/api/portal/treatments/{id}/instructions", activeTreatment))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.instructions").value("Reagendar la sesión esta semana"))
                .andExpect(jsonPath("$.sessionDate").value(day(9)))
                .andExpect(jsonPath("$.sequenceNumber").value(4))
                .andExpect(jsonPath("$.responsibleName").value("Lcdo. Mateo Ríos"));
    }

    @Test
    void theInstructionsCarryNothingElseOfTheSession() throws Exception {
        String body = perform(rosa, get("/api/portal/treatments/{id}/instructions", activeTreatment))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> instruction = JsonPath.read(body, "$");
        assertThat(instruction.keySet())
                .isEqualTo(Set.of("instructions", "sessionDate", "sequenceNumber", "responsibleName"));
        assertThat(body).doesNotContain("Observación clínica interna", "IMPROVING");
    }

    @Test
    void aTreatmentWithoutInstructionsAnswers200WithoutABody() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/instructions", finishedTreatment))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void readingTheInstructionsIsAuditedAsReadOfTheTreatmentAlsoWhenThereAreNone() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/instructions", activeTreatment)).andExpect(status().isOk());
        perform(rosa, get("/api/portal/treatments/{id}/instructions", finishedTreatment)).andExpect(status().isOk());

        assertThat(entriesOf(rosa))
                .allSatisfy(entry -> assertThat(entry)
                        .containsEntry("action", "READ")
                        .containsEntry("entity_type", "Treatment"))
                .extracting(entry -> entry.get("entity_id"))
                .containsExactlyInAnyOrder(activeTreatment, finishedTreatment);
    }

    // ----- access: same rules as HU-12 -----

    @ParameterizedTest
    @ValueSource(strings = {"sessions", "instructions"})
    void aTreatmentOfAnotherPatientAnswers403WithoutItsDataAndIsAuditedAsAccessDenied(String resource)
            throws Exception {
        String body = perform(rosa, get("/api/portal/treatments/{id}/" + resource, treatmentOfLuis))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value(NOT_ACCESSIBLE))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Indicación de otro paciente", "sequenceNumber", "activitiesPerformed");
        assertThat(entriesOf(rosa)).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "ACCESS_DENIED")
                .containsEntry("entity_type", "portal/treatments")
                .containsEntry("entity_id", treatmentOfLuis)
                .containsEntry("ip_address", "127.0.0.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sessions", "instructions"})
    void aTreatmentThatDoesNotExistAnswersTheSame403(String resource) throws Exception {
        Long missing = 999_999_999L;

        perform(rosa, get("/api/portal/treatments/{id}/" + resource, missing))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(NOT_ACCESSIBLE));

        assertThat(entriesOf(rosa)).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "ACCESS_DENIED")
                .containsEntry("entity_type", "portal/treatments")
                .containsEntry("entity_id", missing));
    }

    @Test
    void anAccountWithoutALinkedPatientAnswers404AndIsNotAudited() throws Exception {
        User unlinked = createUser(Role.PATIENT, "Cuenta Sin Paciente");

        perform(unlinked, get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_LINKED_PATIENT));
        perform(unlinked, get("/api/portal/treatments/{id}/instructions", activeTreatment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_LINKED_PATIENT));

        assertThat(entriesOf(unlinked)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesGet403OnTheSessionsAndTheInstructions(Role role) throws Exception {
        User user = createUser(role, "Usuario de otro rol");

        perform(user, get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."));
        perform(user, get("/api/portal/treatments/{id}/instructions", activeTreatment))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."));

        assertThat(entriesOf(user)).hasSize(2).allSatisfy(entry -> assertThat(entry)
                .containsEntry("action", "ACCESS_DENIED")
                .containsEntry("entity_type", "portal/treatments")
                .containsEntry("entity_id", activeTreatment));
    }

    @Test
    void anonymousRequestsGet401() throws Exception {
        mockMvc.perform(get("/api/portal/treatments/{id}/sessions", activeTreatment))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/treatments/{id}/instructions", activeTreatment))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sessions", "instructions"})
    void theSessionsAndTheInstructionsAreReadOnly(String resource) throws Exception {
        String path = "/api/portal/treatments/{id}/" + resource;

        perform(rosa, post(path, activeTreatment).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        perform(rosa, put(path, activeTreatment).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        perform(rosa, delete(path, activeTreatment)).andExpect(status().isMethodNotAllowed());
    }

    // ----- helpers -----

    private void session(User physiotherapist, int sequenceNumber, int day, String time, int painScore,
            String status, String instructions) {
        TestSessions.insert(jdbc, activeTreatment, physiotherapist.getId(), sequenceNumber, start.plusDays(day), time,
                painScore, status, instructions);
    }

    private String day(int daysAfterTheStart) {
        return start.plusDays(daysAfterTheStart).toString();
    }

    private Long insertLinkedPatient(User account, String firstName, String lastName) {
        Long patientId = TestPatients.insert(jdbc, firstName, lastName);
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", account.getId(), patientId);
        return patientId;
    }

    private List<Map<String, Object>> entriesOf(User user) {
        return jdbc.queryForList("SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE user_id = ?",
                user.getId());
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("portal-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
