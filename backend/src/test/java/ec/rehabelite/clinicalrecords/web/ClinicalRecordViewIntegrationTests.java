package ec.rehabelite.clinicalrecords.web;

import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.recordBody;
import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.treatmentBody;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

/**
 * HU-09 end to end: a physiotherapist reads the full clinical record of a patient
 * (GET /api/patients/{id}/clinical-record), audited as READ, and edits its habits and medical history (PUT on the
 * same path), audited as UPDATE.
 */
@AutoConfigureMockMvc
class ClinicalRecordViewIntegrationTests extends AbstractIntegrationTest {

    private static final String EDITED_HISTORY = """
            {"habits":" Fuma 2 cigarrillos al día ","physicalActivity":"Natación","allergies":"Penicilina",
             "currentMedication":"Paracetamol","personalHistory":"Diabetes tipo 2","surgicalHistory":"Ninguno",
             "familyHistory":"Padre hipertenso"}""";

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
    private Long patientId;
    private Long recordId;
    private Long firstTreatmentId;

    @BeforeEach
    void setUp() throws Exception {
        physio = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        physioToken = tokenOf(physio);
        patientId = TestPatients.insert(jdbc, "Juan Carlos", "Pérez Gómez");
        mockMvc.perform(post("/api/patients/{id}/clinical-record", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON).content(recordBody(treatmentBody(6, 12, "M54.5"))))
                .andExpect(status().isCreated());
        recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class, patientId);
        firstTreatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
    }

    @Test
    void returnsThePatientTheHistoryTheActiveTreatmentWithItsResponsibleAndThePreviousOnes() throws Exception {
        // The first treatment is completed by its responsible, who later leaves the clinic; a second one is opened
        // and handed over to another physiotherapist
        mockMvc.perform(patch("/api/treatments/{id}/status", firstTreatmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"COMPLETED\",\"reason\":\"Alta\"}"))
                .andExpect(status().isOk());
        User other = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        mockMvc.perform(post("/api/clinical-records/{id}/treatments", recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON).content(treatmentBody(8, 10, "M75.1")))
                .andExpect(status().isCreated());
        Long activeId = jdbc.queryForObject(
                "SELECT id FROM treatments WHERE clinical_record_id = ? AND status = 'ACTIVE'", Long.class, recordId);
        mockMvc.perform(put("/api/treatments/{id}/responsible", activeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(physioToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"physiotherapistId\":%d}".formatted(other.getId())))
                .andExpect(status().isOk());
        jdbc.update("UPDATE users SET active = false WHERE id = ?", physio.getId());
        String readerToken = tokenOf(other);

        view(readerToken, patientId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.id").value(patientId))
                .andExpect(jsonPath("$.patient.firstName").value("Juan Carlos"))
                .andExpect(jsonPath("$.patient.medicalRecordNumber").value(TestPatients.nationalIdOf(jdbc, patientId)))
                .andExpect(jsonPath("$.patient.age").isNumber())
                .andExpect(jsonPath("$.patient.sex").value("FEMALE"))
                .andExpect(jsonPath("$.patient.email").value("paciente@rehabelite.test"))
                .andExpect(jsonPath("$.patient.healthInsurance").value("Ninguno"))
                .andExpect(jsonPath("$.patient.ethnicSelfIdentification").doesNotExist())
                .andExpect(jsonPath("$.clinicalRecord.id").value(recordId))
                .andExpect(jsonPath("$.clinicalRecord.habits").value("No fuma; café diario"))
                .andExpect(jsonPath("$.clinicalRecord.familyHistory").value("Madre con artrosis"))
                .andExpect(jsonPath("$.activeTreatment.id").value(activeId))
                .andExpect(jsonPath("$.activeTreatment.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activeTreatment.diagnosis.code").value("M75.1"))
                .andExpect(jsonPath("$.activeTreatment.initialPainScore").value(8))
                .andExpect(jsonPath("$.activeTreatment.painLocation").value("Región lumbar"))
                .andExpect(jsonPath("$.activeTreatment.goal").value("Reducir el dolor"))
                .andExpect(jsonPath("$.activeTreatment.plannedSessions").value(10))
                .andExpect(jsonPath("$.activeTreatment.currentResponsible.physiotherapistId").value(other.getId()))
                .andExpect(jsonPath("$.activeTreatment.currentResponsible.fullName").value("Lcdo. Mateo Ríos"))
                .andExpect(jsonPath("$.activeTreatment.currentResponsible.validTo").doesNotExist())
                .andExpect(jsonPath("$.previousTreatments.length()").value(1))
                .andExpect(jsonPath("$.previousTreatments[0].id").value(firstTreatmentId))
                .andExpect(jsonPath("$.previousTreatments[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.previousTreatments[0].diagnosis.code").value("M54.5"))
                .andExpect(jsonPath("$.previousTreatments[0].updatedAt").isNotEmpty())
                // A deactivated physiotherapist keeps their name in the record
                .andExpect(jsonPath("$.previousTreatments[0].responsible.fullName").value("Lcda. Valeria Andrade"));

        assertThat(audit("READ")).containsExactly(other.getId());
    }

    @Test
    void withoutAnActiveTreatmentTheRecordListsOnlyThePreviousOnes() throws Exception {
        jdbc.update("UPDATE treatments SET status = 'ABANDONED' WHERE id = ?", firstTreatmentId);

        view(physioToken, patientId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTreatment").doesNotExist())
                .andExpect(jsonPath("$.previousTreatments[0].status").value("ABANDONED"))
                .andExpect(jsonPath("$.previousTreatments[0].responsible.fullName").value("Lcda. Valeria Andrade"));
    }

    @Test
    void everyReadIsAuditedWithTheRecordId() throws Exception {
        view(physioToken, patientId).andExpect(status().isOk());
        view(physioToken, patientId).andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT user_id, entity_type, entity_id, ip_address FROM audit_logs
                WHERE action = 'READ' AND entity_id = ? AND entity_type = 'ClinicalRecord'""", recordId))
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry)
                        .containsEntry("user_id", physio.getId())
                        .containsEntry("ip_address", "127.0.0.1"));
    }

    @Test
    void unknownPatientsAndPatientsWithoutARecordAre404AndNotAudited() throws Exception {
        Long withoutRecord = TestPatients.insert(jdbc, "Ana Lucía", "Mora Ruiz");

        view(physioToken, withoutRecord)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no tiene historial clínico."));
        view(physioToken, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'READ' AND user_id = ?",
                Integer.class, physio.getId())).isZero();
    }

    @Test
    void editsTheHistoryWithoutTouchingTheTreatmentsAndAuditsTheUpdate() throws Exception {
        User editor = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        Instant before = Instant.now();

        updateHistory(tokenOf(editor), patientId, EDITED_HISTORY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId))
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.habits").value("Fuma 2 cigarrillos al día"))
                .andExpect(jsonPath("$.allergies").value("Penicilina"))
                .andExpect(jsonPath("$.familyHistory").value("Padre hipertenso"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT habits, physical_activity, current_medication, personal_history, created_by, updated_by,
                       updated_at
                FROM clinical_records WHERE id = ?""", recordId);
        assertThat(row)
                .containsEntry("habits", "Fuma 2 cigarrillos al día")
                .containsEntry("physical_activity", "Natación")
                .containsEntry("current_medication", "Paracetamol")
                .containsEntry("personal_history", "Diabetes tipo 2")
                .containsEntry("created_by", physio.getId())
                .containsEntry("updated_by", editor.getId());
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(jdbc.queryForMap("SELECT status, updated_at, chief_complaint FROM treatments WHERE id = ?",
                firstTreatmentId))
                .containsEntry("status", "ACTIVE")
                .containsEntry("updated_at", null)
                .containsEntry("chief_complaint", "Dolor lumbar al levantar peso");
        assertThat(audit("UPDATE")).containsExactly(editor.getId());
    }

    @Test
    void sendingTheSameHistoryChangesAndAuditsNothing() throws Exception {
        updateHistory(physioToken, patientId, EDITED_HISTORY).andExpect(status().isOk());
        Object updatedAt = jdbc.queryForObject("SELECT updated_at FROM clinical_records WHERE id = ?", Object.class,
                recordId);

        updateHistory(physioToken, patientId, EDITED_HISTORY).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT updated_at FROM clinical_records WHERE id = ?", Object.class,
                recordId)).isEqualTo(updatedAt);
        assertThat(audit("UPDATE")).hasSize(1);
    }

    @Test
    void editingValidatesLikeTheCreationAndNeedsARecord() throws Exception {
        updateHistory(physioToken, patientId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(7));
        updateHistory(physioToken, patientId, EDITED_HISTORY.replace("\"Natación\"", "\"  \""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("physicalActivity"));
        updateHistory(physioToken, patientId, EDITED_HISTORY.replace("\"Penicilina\"",
                "\"" + "x".repeat(TreatmentRequest.TEXT_MAX + 1) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("allergies"));
        updateHistory(physioToken, TestPatients.insert(jdbc, "Ana Lucía", "Mora Ruiz"), EDITED_HISTORY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no tiene historial clínico."));
        updateHistory(physioToken, 999_999_999L, EDITED_HISTORY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));

        assertThat(jdbc.queryForObject("SELECT habits FROM clinical_records WHERE id = ?", String.class, recordId))
                .isEqualTo("No fuma; café diario");
        assertThat(audit("UPDATE")).isEmpty();
    }

    @Test
    void onlyPhysiotherapistsReadOrEditTheRecord() throws Exception {
        for (Role role : List.of(Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role, "Usuario de prueba");
            String token = tokenOf(user);
            view(token, patientId).andExpect(status().isForbidden());
            updateHistory(token, patientId, EDITED_HISTORY).andExpect(status().isForbidden());
            assertThat(jdbc.queryForList("""
                    SELECT entity_type || ':' || entity_id FROM audit_logs WHERE user_id = ? AND action = 'ACCESS_DENIED'""",
                    String.class, user.getId()))
                    .containsExactly("patients:" + patientId, "patients:" + patientId);
        }
        mockMvc.perform(get("/api/patients/{id}/clinical-record", patientId)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/patients/{id}/clinical-record", patientId)
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_HISTORY))
                .andExpect(status().isUnauthorized());

        assertThat(audit("READ")).isEmpty();
        assertThat(audit("UPDATE")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT habits FROM clinical_records WHERE id = ?", String.class, recordId))
                .isEqualTo("No fuma; café diario");
    }

    @Test
    void openApiDocumentsTheRecordViewAndItsEdition() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].get.tags[0]")
                        .value("Historial clínico"))
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].get.responses['404']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].put.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].put.responses['404']")
                        .exists());
    }

    private ResultActions view(String token, Long patient) throws Exception {
        return mockMvc.perform(get("/api/patients/{id}/clinical-record", patient)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions updateHistory(String token, Long patient, String body) throws Exception {
        return mockMvc.perform(put("/api/patients/{id}/clinical-record", patient)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<Long> audit(String action) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = 'ClinicalRecord' AND entity_id = ?
                ORDER BY id""", Long.class, action, recordId);
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("expediente-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
