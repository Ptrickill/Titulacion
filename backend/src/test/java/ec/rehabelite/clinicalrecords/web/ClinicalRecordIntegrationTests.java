package ec.rehabelite.clinicalrecords.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.ClinicCalendar;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * HU-07 end to end: a physiotherapist creates the clinical record with its first treatment
 * (POST /api/patients/{id}/clinical-record) and opens new treatments (POST /api/clinical-records/{id}/treatments).
 */
@AutoConfigureMockMvc
class ClinicalRecordIntegrationTests extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        physio = createUser(Role.PHYSIOTHERAPIST);
        physioToken = tokenOf(physio);
        patientId = TestPatients.insert(jdbc, "Juan Carlos", "Pérez Gómez");
    }

    @Test
    void createsTheRecordWithAnActiveTreatmentAndTheAuthorAsCurrentResponsible() throws Exception {
        String location = create(physioToken, patientId, recordBody(treatmentBody(7, 12, "m54.5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.habits").value("No fuma; café diario"))
                .andExpect(jsonPath("$.treatments.length()").value(1))
                .andExpect(jsonPath("$.treatments[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.treatments[0].diagnosis.code").value("M54.5"))
                .andExpect(jsonPath("$.treatments[0].diagnosis.description").value("Lumbago no especificado"))
                .andExpect(jsonPath("$.treatments[0].initialPainScore").value(7))
                .andExpect(jsonPath("$.treatments[0].plannedSessions").value(12))
                .andExpect(jsonPath("$.treatments[0].severity").value("MODERATE"))
                .andExpect(jsonPath("$.treatments[0].startDate").value(ClinicCalendar.today(Clock.systemUTC()).toString()))
                .andExpect(jsonPath("$.treatments[0].responsiblePhysiotherapistId").value(physio.getId()))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);
        Long treatmentId = jdbc.queryForObject(
                "SELECT id FROM treatments WHERE clinical_record_id = ? AND status = 'ACTIVE'", Long.class, recordId);
        assertThat(location).isEqualTo("/api/clinical-records/" + recordId);
        assertThat(jdbc.queryForList("""
                SELECT physiotherapist_id FROM responsible_assignments WHERE treatment_id = ? AND valid_to IS NULL""",
                Long.class, treatmentId)).containsExactly(physio.getId());
        assertThat(audit("ClinicalRecord", recordId)).containsExactly(physio.getId());
        assertThat(audit("Treatment", treatmentId)).containsExactly(physio.getId());
    }

    @Test
    void aSecondRecordForThePatientIs409() throws Exception {
        create(physioToken, patientId, recordBody(treatmentBody(5, 10, "M54.5"))).andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);

        create(tokenOf(createUser(Role.PHYSIOTHERAPIST)), patientId, recordBody(treatmentBody(5, 10, "M54.5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene un historial clínico."))
                .andExpect(jsonPath("$.existingClinicalRecordId").value(recordId));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_records WHERE patient_id = ?", Integer.class,
                patientId)).isEqualTo(1);
    }

    @Test
    void painScoreOutsideZeroToTenIsRejected() throws Exception {
        for (int painScore : new int[] {-1, 11}) {
            create(physioToken, patientId, recordBody(treatmentBody(painScore, 10, "M54.5")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("treatment.initialPainScore"));
        }
        assertThat(recordCount()).isZero();
    }

    @Test
    void zeroPlannedSessionsAreRejected() throws Exception {
        create(physioToken, patientId, recordBody(treatmentBody(5, 0, "M54.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("treatment.plannedSessions"));
        assertThat(recordCount()).isZero();
    }

    @Test
    void aDiagnosisOutsideTheCatalogIsRejected() throws Exception {
        create(physioToken, patientId, recordBody(treatmentBody(5, 10, "Z99.9")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El diagnóstico CIE-10 no existe en el catálogo."));
        assertThat(recordCount()).isZero();
    }

    @Test
    void missingDataAndUnknownPatientsAreRejected() throws Exception {
        create(physioToken, patientId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(8));
        create(physioToken, 999_999_999L, recordBody(treatmentBody(5, 10, "M54.5")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));
        assertThat(recordCount()).isZero();
    }

    @Test
    void onlyPhysiotherapistsCreateClinicalRecords() throws Exception {
        for (Role role : List.of(Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role);
            create(tokenOf(user), patientId, recordBody(treatmentBody(5, 10, "M54.5")))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForList("""
                    SELECT entity_type || ':' || entity_id FROM audit_logs WHERE user_id = ? AND action = 'ACCESS_DENIED'""",
                    String.class, user.getId())).containsExactly("patients:" + patientId);
        }
        mockMvc.perform(post("/api/patients/{id}/clinical-record", patientId)
                        .contentType(MediaType.APPLICATION_JSON).content(recordBody(treatmentBody(5, 10, "M54.5"))))
                .andExpect(status().isUnauthorized());
        assertThat(recordCount()).isZero();
    }

    @Test
    void opensANewTreatmentOnlyWhenNoneIsActive() throws Exception {
        create(physioToken, patientId, recordBody(treatmentBody(5, 10, "M54.5"))).andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);
        User otherPhysio = createUser(Role.PHYSIOTHERAPIST);

        openTreatment(tokenOf(otherPhysio), recordId, treatmentBody(4, 8, "M75.1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene un tratamiento activo."));

        // HU-08 will close treatments through the API; here the first one is closed directly
        jdbc.update("UPDATE treatments SET status = 'COMPLETED' WHERE clinical_record_id = ?", recordId);
        openTreatment(tokenOf(otherPhysio), recordId, treatmentBody(4, 8, "M75.1"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/treatments/")))
                .andExpect(jsonPath("$.clinicalRecordId").value(recordId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.diagnosis.code").value("M75.1"))
                .andExpect(jsonPath("$.responsiblePhysiotherapistId").value(otherPhysio.getId()));

        Long newTreatmentId = jdbc.queryForObject(
                "SELECT id FROM treatments WHERE clinical_record_id = ? AND status = 'ACTIVE'", Long.class, recordId);
        assertThat(audit("Treatment", newTreatmentId)).containsExactly(otherPhysio.getId());
        openTreatment(physioToken, 999_999_999L, treatmentBody(4, 8, "M75.1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El historial clínico no existe."));
        openTreatment(tokenOf(createUser(Role.ADMINISTRATOR)), recordId, treatmentBody(4, 8, "M75.1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsTheClinicalRecordEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].post.tags[0]")
                        .value("Historial clínico"))
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/clinical-record'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clinical-records/{clinicalRecordId}/treatments'].post.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/icd10-diagnoses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/physio/patients'].get").exists());
    }

    static String recordBody(String treatment) {
        return """
                {"habits":"No fuma; café diario","physicalActivity":"Camina 30 minutos","allergies":"Ninguna conocida",
                 "currentMedication":"Ibuprofeno 400 mg","personalHistory":"Hipertensión controlada",
                 "surgicalHistory":"Apendicectomía (2010)","familyHistory":"Madre con artrosis","treatment":%s}"""
                .formatted(treatment);
    }

    static String treatmentBody(int painScore, int plannedSessions, String diagnosisCode) {
        return """
                {"diagnosisCode":"%s","chiefComplaint":"Dolor lumbar al levantar peso",
                 "currentIllness":"Tres semanas de evolución","referringPhysician":"Dr. Carlos Andrade",
                 "complementaryTests":"Radiografía lumbar","severity":"MODERATE","painType":"Punzante",
                 "painLocation":"Región lumbar","initialPainScore":%d,"painFrequency":"Intermitente",
                 "sessionFrequency":"3 veces por semana","goal":"Reducir el dolor","plannedSessions":%d}"""
                .formatted(diagnosisCode, painScore, plannedSessions);
    }

    private ResultActions create(String token, Long patient, String body) throws Exception {
        return mockMvc.perform(post("/api/patients/{id}/clinical-record", patient)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions openTreatment(String token, Long recordId, String body) throws Exception {
        return mockMvc.perform(post("/api/clinical-records/{id}/treatments", recordId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Integer recordCount() {
        return jdbc.queryForObject("SELECT count(*) FROM clinical_records WHERE patient_id = ?", Integer.class,
                patientId);
    }

    private List<Long> audit(String entityType, Long entityId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = 'CREATE' AND entity_type = ? AND entity_id = ?""",
                Long.class, entityType, entityId);
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("historial-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
