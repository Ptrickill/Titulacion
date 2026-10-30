package ec.rehabelite.clinicalrecords.web;

import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.recordBody;
import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.treatmentBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-08 end to end: a physiotherapist changes the responsible (PUT /api/treatments/{id}/responsible) and the status
 * (PATCH /api/treatments/{id}/status) of a treatment, and reads both histories (GET /api/treatments/{id}/history).
 */
@AutoConfigureMockMvc
class TreatmentIntegrationTests extends AbstractIntegrationTest {

    private static final String TRANSITION_NOT_ALLOWED = "Transición de estado no permitida.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User physio;
    private String physioToken;
    private Long recordId;
    private Long treatmentId;

    @BeforeEach
    void setUp() throws Exception {
        physio = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        physioToken = tokenOf(physio);
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        mockMvc.perform(post("/api/patients/{id}/clinical-record", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken)
                        .contentType(MediaType.APPLICATION_JSON).content(recordBody(treatmentBody(6, 12, "M54.5"))))
                .andExpect(status().isCreated());
        recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class, patientId);
        treatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
    }

    @Test
    void returnsTheTreatmentWithItsPatientAndPlan() throws Exception {
        mockMvc.perform(get("/api/treatments/{id}", treatmentId).header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(treatmentId))
                .andExpect(jsonPath("$.clinicalRecordId").value(recordId))
                .andExpect(jsonPath("$.patientId").isNumber())
                .andExpect(jsonPath("$.goal").value("Reducir el dolor"))
                .andExpect(jsonPath("$.plannedSessions").value(12))
                .andExpect(jsonPath("$.responsiblePhysiotherapistId").value(physio.getId()));
        mockMvc.perform(get("/api/treatments/{id}", 999_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El tratamiento no existe."));
    }

    @Test
    void changingTheResponsibleEndsTheCurrentAssignmentAndKeepsASingleCurrentOne() throws Exception {
        User other = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");

        changeResponsible(tokenOf(other), other.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentResponsible.physiotherapistId").value(other.getId()))
                .andExpect(jsonPath("$.currentResponsible.fullName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.currentResponsible.validTo").doesNotExist())
                .andExpect(jsonPath("$.responsibles.length()").value(2))
                .andExpect(jsonPath("$.responsibles[0].physiotherapistId").value(other.getId()))
                .andExpect(jsonPath("$.responsibles[1].physiotherapistId").value(physio.getId()))
                .andExpect(jsonPath("$.responsibles[1].fullName").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.responsibles[1].validTo").isNotEmpty());

        assertThat(jdbc.queryForList("""
                SELECT physiotherapist_id FROM responsible_assignments WHERE treatment_id = ? AND valid_to IS NULL""",
                Long.class, treatmentId)).containsExactly(other.getId());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM responsible_assignments
                WHERE treatment_id = ? AND physiotherapist_id = ? AND valid_to IS NOT NULL""",
                Integer.class, treatmentId, physio.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM treatments WHERE id = ?", Long.class, treatmentId))
                .isEqualTo(other.getId());
        assertThat(audit("UPDATE")).containsExactly(other.getId());
    }

    @Test
    void theCurrentResponsibleChangesAndAuditsNothing() throws Exception {
        changeResponsible(physioToken, physio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibles.length()").value(1))
                .andExpect(jsonPath("$.currentResponsible.physiotherapistId").value(physio.getId()));

        assertThat(assignmentCount()).isEqualTo(1);
        assertThat(audit("UPDATE")).isEmpty();
    }

    @Test
    void theNewResponsibleMustBeAnActivePhysiotherapist() throws Exception {
        User inactive = createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta inactivo");
        inactive.deactivate(null, Instant.now());
        userRepository.save(inactive);
        List<Long> invalid = List.of(inactive.getId(), createUser(Role.ADMINISTRATOR, "Admin").getId(),
                createUser(Role.PATIENT, "Paciente").getId(), createUser(Role.OPERATIONS_DIRECTOR, "Dirección").getId(),
                999_999_999L);

        for (Long userId : invalid) {
            changeResponsible(physioToken, userId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("El nuevo responsable debe ser un fisioterapeuta activo."));
        }
        mockMvc.perform(put("/api/treatments/{id}/responsible", treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("physiotherapistId"));

        assertThat(assignmentCount()).isEqualTo(1);
        assertThat(audit("UPDATE")).isEmpty();
    }

    @Test
    void theResponsibleOfAClosedTreatmentDoesNotChange() throws Exception {
        changeStatus(physioToken, "COMPLETED", "Alta por objetivos cumplidos").andExpect(status().isOk());
        User other = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");

        changeResponsible(physioToken, other.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Solo se puede cambiar el responsable de un tratamiento activo."));

        assertThat(assignmentCount()).isEqualTo(1);
    }

    @Test
    void allowedTransitionsKeepPreviousAndNewStatusReasonUserAndDate() throws Exception {
        User other = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        changeStatus(physioToken, "ABANDONED", "No asiste desde hace un mes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.allowedNextStatuses").value(contains("ACTIVE")));
        changeStatus(tokenOf(other), "ACTIVE", "  Retoma la terapia  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses")
                        .value(contains("COMPLETED", "ABANDONED")));
        changeStatus(physioToken, "COMPLETED", "Alta por objetivos cumplidos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.allowedNextStatuses").isEmpty())
                .andExpect(jsonPath("$.statusChanges.length()").value(3))
                .andExpect(jsonPath("$.statusChanges[0].previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.statusChanges[0].newStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.statusChanges[0].changedByName").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.statusChanges[1].reason").value("Retoma la terapia"))
                .andExpect(jsonPath("$.statusChanges[1].changedById").value(other.getId()))
                .andExpect(jsonPath("$.statusChanges[1].changedByName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.statusChanges[2].newStatus").value("ABANDONED"));

        List<String> rows = jdbc.queryForList("""
                SELECT previous_status || '>' || new_status || '|' || reason || '|' || user_id
                FROM treatment_status_changes WHERE treatment_id = ? ORDER BY id""",
                String.class, treatmentId);
        assertThat(rows).containsExactly(
                "ACTIVE>ABANDONED|No asiste desde hace un mes|" + physio.getId(),
                "ABANDONED>ACTIVE|Retoma la terapia|" + other.getId(),
                "ACTIVE>COMPLETED|Alta por objetivos cumplidos|" + physio.getId());
        assertThat(jdbc.queryForObject("SELECT status FROM treatments WHERE id = ?", String.class, treatmentId))
                .isEqualTo("COMPLETED");
        assertThat(audit("STATUS_CHANGE")).containsExactly(physio.getId(), other.getId(), physio.getId());
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, ACTIVE",
        "COMPLETED, ACTIVE",
        "COMPLETED, ABANDONED",
        "COMPLETED, COMPLETED",
        "ABANDONED, COMPLETED",
        "ABANDONED, ABANDONED"})
    void anyOtherTransitionIsRejected(String from, String to) throws Exception {
        jdbc.update("UPDATE treatments SET status = ? WHERE id = ?", from, treatmentId);

        changeStatus(physioToken, to, "Motivo del cambio")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(TRANSITION_NOT_ALLOWED));

        assertThat(jdbc.queryForObject("SELECT status FROM treatments WHERE id = ?", String.class, treatmentId))
                .isEqualTo(from);
        assertThat(statusChangeCount()).isZero();
        assertThat(audit("STATUS_CHANGE")).isEmpty();
    }

    @Test
    void anAbandonedTreatmentIsNotResumedWhileAnotherOneIsActive() throws Exception {
        changeStatus(physioToken, "ABANDONED", "No asiste").andExpect(status().isOk());
        mockMvc.perform(post("/api/clinical-records/{id}/treatments", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON).content(treatmentBody(4, 8, "M75.1")))
                .andExpect(status().isCreated());

        changeStatus(physioToken, "ACTIVE", "Retoma")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene un tratamiento activo."));

        assertThat(jdbc.queryForObject("SELECT status FROM treatments WHERE id = ?", String.class, treatmentId))
                .isEqualTo("ABANDONED");
        assertThat(statusChangeCount()).isEqualTo(1);
    }

    @Test
    void theNewStatusAndAReasonAreRequired() throws Exception {
        for (String body : List.of(
                "{\"newStatus\":\"COMPLETED\",\"reason\":\"   \"}",
                "{\"newStatus\":\"COMPLETED\",\"reason\":\"%s\"}".formatted("a".repeat(1001)),
                "{\"newStatus\":\"COMPLETED\"}")) {
            patchStatus(physioToken, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        patchStatus(physioToken, "{\"reason\":\"Alta\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newStatus"));
        patchStatus(physioToken, "{\"newStatus\":\"PAUSED\",\"reason\":\"Alta\"}")
                .andExpect(status().isBadRequest());

        assertThat(statusChangeCount()).isZero();
    }

    @Test
    void theHistoryListsResponsiblesAndStatusChangesMostRecentFirst() throws Exception {
        User other = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        changeResponsible(physioToken, other.getId()).andExpect(status().isOk());
        changeStatus(tokenOf(other), "ABANDONED", "No asiste").andExpect(status().isOk());

        mockMvc.perform(get("/api/treatments/{id}/history", treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatmentId").value(treatmentId))
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.currentResponsible.fullName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.responsibles[0].fullName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.responsibles[1].fullName").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.statusChanges.length()").value(1))
                .andExpect(jsonPath("$.statusChanges[0].reason").value("No asiste"));

        for (ResultActions unknown : List.of(
                mockMvc.perform(get("/api/treatments/{id}/history", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))),
                changeStatusOf(999_999_999L, "COMPLETED"),
                mockMvc.perform(put("/api/treatments/{id}/responsible", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"physiotherapistId\":%d}".formatted(physio.getId()))))) {
            unknown.andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("El tratamiento no existe."));
        }
    }

    @Test
    void listsTheActivePhysiotherapists() throws Exception {
        User inactive = createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta inactivo " + UUID.randomUUID());
        inactive.deactivate(null, Instant.now());
        userRepository.save(inactive);
        User admin = createUser(Role.ADMINISTRATOR, "Administrador " + UUID.randomUUID());

        String body = mockMvc.perform(get("/api/physio/physiotherapists")
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"id\":" + physio.getId() + ",")
                .doesNotContain("\"id\":" + inactive.getId() + ",")
                .doesNotContain("\"id\":" + admin.getId() + ",");
    }

    @Test
    void onlyPhysiotherapistsChangeTreatments() throws Exception {
        for (Role role : List.of(Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role, "Usuario " + role);
            String token = tokenOf(user);
            changeStatus(token, "COMPLETED", "Alta").andExpect(status().isForbidden());
            changeResponsible(token, physio.getId()).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/treatments/{id}/history", treatmentId).header(HttpHeaders.AUTHORIZATION,
                    bearer(token))).andExpect(status().isForbidden());
            assertThat(jdbc.queryForList("""
                    SELECT entity_type || ':' || entity_id FROM audit_logs WHERE user_id = ? AND action = 'ACCESS_DENIED'""",
                    String.class, user.getId())).containsOnly("treatments:" + treatmentId).hasSize(3);
        }
        mockMvc.perform(patch("/api/treatments/{id}/status", treatmentId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"COMPLETED\",\"reason\":\"Alta\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(statusChangeCount()).isZero();
    }

    @Test
    void openApiDocumentsTheTreatmentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/treatments/{treatmentId}'].get.tags[0]").value("Historial clínico"))
                .andExpect(jsonPath("$.paths['/api/treatments/{treatmentId}/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/treatments/{treatmentId}/responsible'].put.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/treatments/{treatmentId}/status'].patch.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/physio/physiotherapists'].get").exists());
    }

    private ResultActions changeResponsible(String token, Long physiotherapistId) throws Exception {
        return mockMvc.perform(put("/api/treatments/{id}/responsible", treatmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"physiotherapistId\":%d}".formatted(physiotherapistId)));
    }

    private ResultActions changeStatus(String token, String newStatus, String reason) throws Exception {
        return patchStatus(token, "{\"newStatus\":\"%s\",\"reason\":\"%s\"}".formatted(newStatus, reason));
    }

    private ResultActions changeStatusOf(Long treatment, String newStatus) throws Exception {
        return mockMvc.perform(patch("/api/treatments/{id}/status", treatment)
                .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"%s\",\"reason\":\"Alta\"}".formatted(newStatus)));
    }

    private ResultActions patchStatus(String token, String body) throws Exception {
        return mockMvc.perform(patch("/api/treatments/{id}/status", treatmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Integer assignmentCount() {
        return jdbc.queryForObject("SELECT count(*) FROM responsible_assignments WHERE treatment_id = ?",
                Integer.class, treatmentId);
    }

    private Integer statusChangeCount() {
        return jdbc.queryForObject("SELECT count(*) FROM treatment_status_changes WHERE treatment_id = ?",
                Integer.class, treatmentId);
    }

    private List<Long> audit(String action) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = 'Treatment' AND entity_id = ?
                ORDER BY id""", Long.class, action, treatmentId);
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("tratamiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
