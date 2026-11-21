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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-12: the patient portal. A patient sees only the treatments of the patient linked to the own account: the list,
 * with the sessions that took place against the planned ones, and the progress of one of them with the evolution of
 * the pain. Nothing can be edited, and a treatment of another patient answers 403.
 * <p>
 * Rosa has three treatments (EVA and planned sessions in brackets):
 * <pre>
 *  completed  started 200 days ago  [8, 10]  responsible Mateo    2 sessions that took place
 *  abandoned  started 100 days ago  [6, 8]   no responsible       no sessions
 *  active     started  30 days ago  [7, 12]  responsible Valeria  the five sessions below
 *
 *  #1  day 2  10:00  COMPLETED    EVA 6
 *  #2  day 5  08:00  RESCHEDULED  EVA 6
 *  #3  day 2  08:00  COMPLETED    EVA 5
 *  #4  day 5  09:00  NO_SHOW      EVA 5
 *  #5  day 9  09:00  COMPLETED    EVA 3
 * </pre>
 * Luis is another patient, with one active treatment.
 */
@AutoConfigureMockMvc
class PortalIntegrationTests extends AbstractIntegrationTest {

    private static final String NOT_ACCESSIBLE = "No tiene acceso a ese tratamiento.";
    private static final String NO_LINKED_PATIENT = "La cuenta no tiene un paciente vinculado.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User rosa;
    private User luis;
    private Long rosaPatientId;
    private Long activeTreatment;
    private Long abandonedTreatment;
    private Long completedTreatment;
    private Long treatmentOfLuis;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        User valeria = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        User mateo = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        LocalDate today = LocalDate.now();

        rosa = createUser(Role.PATIENT, "Rosa Elena Ortiz Vega");
        rosaPatientId = insertLinkedPatient(rosa, "Rosa Elena", "Ortiz Vega");
        Long record = TestTreatments.insertRecord(jdbc, rosaPatientId);
        // Inserted from the oldest to the most recent: the order of the answer does not come from the ids alone
        completedTreatment = TestTreatments.insertFor(jdbc, record, "COMPLETED", today.minusDays(200), 8, 10);
        TestTreatments.assignResponsible(jdbc, completedTreatment, mateo.getId());
        TestSessions.insert(jdbc, completedTreatment, mateo.getId(), 1, today.minusDays(190), "09:00", 6, "COMPLETED");
        TestSessions.insert(jdbc, completedTreatment, mateo.getId(), 2, today.minusDays(180), "09:00", 2, "COMPLETED");
        activeTreatment = TestTreatments.insertFor(jdbc, record, "ACTIVE", today.minusDays(30), 7, 12);
        TestTreatments.assignResponsible(jdbc, activeTreatment, valeria.getId());
        abandonedTreatment = TestTreatments.insertFor(jdbc, record, "ABANDONED", today.minusDays(100), 6, 8);

        start = today.minusDays(30);
        TestSessions.insert(jdbc, activeTreatment, valeria.getId(), 1, start.plusDays(2), "10:00", 6, "COMPLETED");
        TestSessions.insert(jdbc, activeTreatment, mateo.getId(), 2, start.plusDays(5), "08:00", 6, "RESCHEDULED");
        TestSessions.insert(jdbc, activeTreatment, mateo.getId(), 3, start.plusDays(2), "08:00", 5, "COMPLETED");
        TestSessions.insert(jdbc, activeTreatment, valeria.getId(), 4, start.plusDays(5), "09:00", 5, "NO_SHOW");
        TestSessions.insert(jdbc, activeTreatment, valeria.getId(), 5, start.plusDays(9), "09:00", 3, "COMPLETED");

        luis = createUser(Role.PATIENT, "Luis Alberto Quishpe Tipán");
        Long luisPatientId = insertLinkedPatient(luis, "Luis Alberto", "Quishpe Tipán");
        treatmentOfLuis = TestTreatments.insertFor(jdbc, TestTreatments.insertRecord(jdbc, luisPatientId), "ACTIVE",
                today.minusDays(10), 9, 6);
        TestSessions.insert(jdbc, treatmentOfLuis, valeria.getId(), 1, today.minusDays(5), "09:00", 8, "COMPLETED");
    }

    // ----- GET /api/portal/treatments -----

    @Test
    void theActiveTreatmentComesFirstAndThenTheOthersFromTheMostRecentToTheOldest() throws Exception {
        perform(rosa, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(activeTreatment.intValue(), abandonedTreatment.intValue(),
                        completedTreatment.intValue())))
                .andExpect(jsonPath("$[*].status", contains("ACTIVE", "ABANDONED", "COMPLETED")))
                .andExpect(jsonPath("$[*].active", contains(true, false, false)));
    }

    @Test
    void eachTreatmentHasItsDiagnosisResponsibleAndSessionsThatTookPlaceAgainstThePlannedOnes() throws Exception {
        perform(rosa, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diagnosis.code").value("M54.5"))
                .andExpect(jsonPath("$[0].diagnosis.description").isNotEmpty())
                .andExpect(jsonPath("$[0].startDate").value(start.toString()))
                .andExpect(jsonPath("$[*].responsibleName",
                        contains("Lcda. Valeria Andrade", null, "Lcdo. Mateo Ríos")))
                .andExpect(jsonPath("$[*].plannedSessions", contains(12, 8, 10)))
                // Only COMPLETED sessions: the rescheduled one and the no-show of the active treatment do not count
                .andExpect(jsonPath("$[*].completedSessions", contains(3, 0, 2)));
    }

    @Test
    void theListNeverCarriesThePatientIdNorTheClinicalContentOfTheTreatment() throws Exception {
        String body = perform(rosa, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("patientId", "responsibleId", "chiefComplaint", "goal", "Dolor lumbar",
                "Observación clínica interna");
    }

    @Test
    void aPatientOnlySeesTheOwnTreatments() throws Exception {
        perform(luis, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(treatmentOfLuis.intValue())))
                .andExpect(jsonPath("$[0].completedSessions").value(1))
                .andExpect(jsonPath("$[0].responsibleName").isEmpty());
    }

    @Test
    void aPatientWithoutTreatmentsGetsAnEmptyList() throws Exception {
        User withoutRecord = createUser(Role.PATIENT, "Daniela Sofía Zambrano Loor");
        insertLinkedPatient(withoutRecord, "Daniela Sofía", "Zambrano Loor");
        User withEmptyRecord = createUser(Role.PATIENT, "Paciente Con Historial");
        TestTreatments.insertRecord(jdbc, insertLinkedPatient(withEmptyRecord, "Paciente", "Con Historial"));

        perform(withoutRecord, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        perform(withEmptyRecord, get("/api/portal/treatments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        assertThat(entriesOf(withoutRecord)).isEmpty();
        assertThat(entriesOf(withEmptyRecord)).isEmpty();
    }

    @Test
    void listingAuditsOneReadPerTreatment() throws Exception {
        perform(rosa, get("/api/portal/treatments")).andExpect(status().isOk());

        assertThat(entriesOf(rosa))
                .allSatisfy(entry -> assertThat(entry)
                        .containsEntry("action", "READ")
                        .containsEntry("entity_type", "Treatment")
                        .containsEntry("ip_address", "127.0.0.1"))
                .extracting(entry -> entry.get("entity_id"))
                .containsExactlyInAnyOrder(activeTreatment, abandonedTreatment, completedTreatment);
    }

    // ----- GET /api/portal/treatments/{id}/progress -----

    @Test
    void theProgressComparesTheSessionsThatTookPlaceWithThePlannedOnes() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatmentId").value(activeTreatment))
                .andExpect(jsonPath("$.completedSessions").value(3))
                .andExpect(jsonPath("$.plannedSessions").value(12))
                .andExpect(jsonPath("$.percentage").value(25));
    }

    @Test
    void thePainCurveStartsWithTheInitialEvaluationAndLeavesOutRescheduledAndNoShowSessions() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isOk())
                // Chronological: sessions 3 and 1 are of the same day, at 08:00 and at 10:00
                .andExpect(jsonPath("$.painCurve[*].label",
                        contains("Evaluación inicial", "Sesión 3", "Sesión 1", "Sesión 5")))
                .andExpect(jsonPath("$.painCurve[*].painScore", contains(7, 5, 6, 3)))
                .andExpect(jsonPath("$.painCurve[*].date", contains(start.toString(),
                        start.plusDays(2).toString(), start.plusDays(2).toString(), start.plusDays(9).toString())))
                .andExpect(jsonPath("$.painCurve[0].sequenceNumber").isEmpty())
                .andExpect(jsonPath("$.painCurve[1].sequenceNumber").value(3));
    }

    @Test
    void theProgressHasTheInitialAndTheLastPainScoreAndTheReductionInPoints() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialPainScore").value(7))
                .andExpect(jsonPath("$.lastPainScore").value(3))
                .andExpect(jsonPath("$.painReduction").value(4));
        // A finished treatment is still the patient's: 8 at the start, 2 in its last session
        perform(rosa, get("/api/portal/treatments/{id}/progress", completedTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessions").value(2))
                .andExpect(jsonPath("$.percentage").value(20))
                .andExpect(jsonPath("$.painReduction").value(6));
    }

    @Test
    void aTreatmentWithoutSessionsHasOnlyTheInitialEvaluation() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/progress", abandonedTreatment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessions").value(0))
                .andExpect(jsonPath("$.percentage").value(0))
                .andExpect(jsonPath("$.painCurve", hasSize(1)))
                .andExpect(jsonPath("$.painCurve[0].label").value("Evaluación inicial"))
                .andExpect(jsonPath("$.lastPainScore").isEmpty())
                .andExpect(jsonPath("$.painReduction").isEmpty());
    }

    @Test
    void theProgressNeverCarriesTheInternalObservationsOfTheSessions() throws Exception {
        String body = perform(rosa, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("clinicalObservations", "Observación clínica interna", "patientId",
                "responsible");
    }

    @Test
    void readingTheProgressIsAuditedAsReadOfTheTreatment() throws Exception {
        perform(rosa, get("/api/portal/treatments/{id}/progress", activeTreatment)).andExpect(status().isOk());

        assertThat(entriesOf(rosa)).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Treatment")
                .containsEntry("entity_id", activeTreatment)
                .containsEntry("ip_address", "127.0.0.1"));
    }

    // ----- access -----

    @Test
    void aTreatmentOfAnotherPatientAnswers403WithoutItsDataAndIsAuditedAsAccessDenied() throws Exception {
        String body = perform(rosa, get("/api/portal/treatments/{id}/progress", treatmentOfLuis))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value(NOT_ACCESSIBLE))
                .andExpect(jsonPath("$.status").value(403))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("painCurve", "plannedSessions", "initialPainScore", "M54.5");
        assertThat(entriesOf(rosa)).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "ACCESS_DENIED")
                .containsEntry("entity_type", "portal/treatments")
                .containsEntry("entity_id", treatmentOfLuis)
                .containsEntry("ip_address", "127.0.0.1"));
    }

    @Test
    void aTreatmentThatDoesNotExistAnswersTheSame403SoTheApiDoesNotRevealWhichIdsExist() throws Exception {
        Long missing = 999_999_999L;

        String forMissing = perform(rosa, get("/api/portal/treatments/{id}/progress", missing))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value(NOT_ACCESSIBLE))
                .andReturn().getResponse().getContentAsString();
        String forForeign = perform(rosa, get("/api/portal/treatments/{id}/progress", treatmentOfLuis))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        // Apart from the path of the request, both answers are the same
        assertThat(withoutInstance(forMissing)).isEqualTo(withoutInstance(forForeign));
        assertThat(entriesOf(rosa))
                .allSatisfy(entry -> assertThat(entry)
                        .containsEntry("action", "ACCESS_DENIED")
                        .containsEntry("entity_type", "portal/treatments"))
                .extracting(entry -> entry.get("entity_id"))
                .containsExactlyInAnyOrder(missing, treatmentOfLuis);
    }

    @Test
    void anAccountWithoutALinkedPatientAnswers404AndIsNotAudited() throws Exception {
        User unlinked = createUser(Role.PATIENT, "Cuenta Sin Paciente");

        perform(unlinked, get("/api/portal/treatments"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(NO_LINKED_PATIENT));
        perform(unlinked, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_LINKED_PATIENT));

        assertThat(entriesOf(unlinked)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesGet403OnThePortal(Role role) throws Exception {
        User user = createUser(role, "Usuario de otro rol");

        perform(user, get("/api/portal/treatments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."));
        perform(user, get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."));

        assertThat(entriesOf(user))
                .allSatisfy(entry -> assertThat(entry).containsEntry("action", "ACCESS_DENIED"))
                .extracting(entry -> entry.get("entity_type"))
                .containsExactlyInAnyOrder("portal", "portal/treatments");
    }

    @Test
    void anonymousRequestsGet401() throws Exception {
        mockMvc.perform(get("/api/portal/treatments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thePortalIsReadOnly() throws Exception {
        perform(rosa, post("/api/portal/treatments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        perform(rosa, put("/api/portal/treatments/{id}/progress", activeTreatment)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        perform(rosa, delete("/api/portal/treatments/{id}/progress", activeTreatment))
                .andExpect(status().isMethodNotAllowed());
    }

    // ----- helpers -----

    private Long insertLinkedPatient(User account, String firstName, String lastName) {
        Long patientId = TestPatients.insert(jdbc, firstName, lastName);
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", account.getId(), patientId);
        return patientId;
    }

    /** The problem response as a map, without the path of the request. */
    private static Map<String, Object> withoutInstance(String problem) {
        Map<String, Object> fields = new HashMap<>(JsonPath.<Map<String, Object>>read(problem, "$"));
        assertThat(fields.remove("instance")).isNotNull();
        return fields;
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
