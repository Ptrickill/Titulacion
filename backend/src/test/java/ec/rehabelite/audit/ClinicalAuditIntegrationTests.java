package ec.rehabelite.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * HU-18: every operation on clinical data of the patients and clinicalrecords endpoints leaves one audit entry with
 * the user, the action, the entity and its id, the date and the client IP. Personal data reads are not audited, and
 * neither are rejected requests.
 */
@AutoConfigureMockMvc
class ClinicalAuditIntegrationTests extends AbstractIntegrationTest {

    private static final String LOCAL_IP = "127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User physio;
    private Instant start;

    @BeforeEach
    void setUp() {
        admin = createUser(Role.ADMINISTRATOR);
        physio = createUser(Role.PHYSIOTHERAPIST);
        start = Instant.now().minusSeconds(1);
    }

    // ----- patients: personal data -----

    @Test
    void registeringAPatientIsAuditedAsCreate() throws Exception {
        String nationalId = TestPatients.randomNationalId();
        perform(admin, post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                .content(patientBody("\"nationalId\":\"%s\",".formatted(nationalId))))
                .andExpect(status().isCreated());

        Long patientId = jdbc.queryForObject("SELECT id FROM patients WHERE national_id = ?", Long.class,
                nationalId);
        assertSingleEntry(admin, "CREATE", "Patient", patientId);
    }

    @Test
    void updatingAPatientIsAuditedAsUpdate() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");

        perform(admin, put("/api/patients/{id}", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(patientBody("\"admissionDate\":\"2026-01-15\",")))
                .andExpect(status().isOk());

        assertSingleEntry(admin, "UPDATE", "Patient", patientId);
    }

    @Test
    void linkingAPortalAccountIsAuditedAsAnUpdateOfThePatient() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Luis Alberto", "Quishpe Tipán");

        perform(admin, post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"portal-%s@rehabelite.test","fullName":"Luis Alberto Quishpe Tipán","role":"PATIENT",
                 "password":"Clave.Segura2026","patientId":%d}""".formatted(UUID.randomUUID(), patientId)))
                .andExpect(status().isCreated());

        assertSingleEntry(admin, "UPDATE", "Patient", patientId);
    }

    @Test
    void readingPersonalDataIsNotAudited() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Ana Lucía", "Mora Ruiz");

        perform(physio, get("/api/patients/{id}", patientId)).andExpect(status().isOk());
        perform(physio, get("/api/patients").param("q", "mora")).andExpect(status().isOk());
        perform(admin, get("/api/patients/portal-candidates")).andExpect(status().isOk());

        assertThat(entriesOf(physio)).isEmpty();
        assertThat(entriesOf(admin)).isEmpty();
    }

    // ----- clinicalrecords: clinical data -----

    @Test
    void creatingTheClinicalRecordIsAuditedForTheRecordAndItsFirstTreatment() throws Exception {
        ClinicalData data = createRecord();

        assertSingleEntry(physio, "CREATE", "ClinicalRecord", data.recordId());
        assertSingleEntry(physio, "CREATE", "Treatment", data.treatmentId());
    }

    @Test
    void readingTheClinicalRecordIsAuditedAsRead() throws Exception {
        ClinicalData data = createRecord();
        User reader = createUser(Role.PHYSIOTHERAPIST);

        perform(reader, get("/api/patients/{id}/clinical-record", data.patientId())).andExpect(status().isOk());

        assertSingleEntry(reader, "READ", "ClinicalRecord", data.recordId());
    }

    @Test
    void editingTheHistoryIsAuditedAsUpdate() throws Exception {
        ClinicalData data = createRecord();
        User editor = createUser(Role.PHYSIOTHERAPIST);

        perform(editor, put("/api/patients/{id}/clinical-record", data.patientId())
                .contentType(MediaType.APPLICATION_JSON).content(historyBody("Fuma 2 cigarrillos al día")))
                .andExpect(status().isOk());

        assertSingleEntry(editor, "UPDATE", "ClinicalRecord", data.recordId());
    }

    @Test
    void openingATreatmentIsAuditedAsCreate() throws Exception {
        ClinicalData data = createRecord();
        jdbc.update("UPDATE treatments SET status = 'COMPLETED' WHERE id = ?", data.treatmentId());
        User other = createUser(Role.PHYSIOTHERAPIST);

        perform(other, post("/api/clinical-records/{id}/treatments", data.recordId())
                .contentType(MediaType.APPLICATION_JSON).content(treatmentBody()))
                .andExpect(status().isCreated());

        Long opened = jdbc.queryForObject(
                "SELECT id FROM treatments WHERE clinical_record_id = ? AND status = 'ACTIVE'", Long.class,
                data.recordId());
        assertSingleEntry(other, "CREATE", "Treatment", opened);
    }

    @Test
    void readingATreatmentIsAuditedAsRead() throws Exception {
        ClinicalData data = createRecord();
        User reader = createUser(Role.PHYSIOTHERAPIST);

        perform(reader, get("/api/treatments/{id}", data.treatmentId())).andExpect(status().isOk());

        assertSingleEntry(reader, "READ", "Treatment", data.treatmentId());
    }

    @Test
    void readingTheTreatmentHistoryIsAuditedAsRead() throws Exception {
        ClinicalData data = createRecord();
        User reader = createUser(Role.PHYSIOTHERAPIST);

        perform(reader, get("/api/treatments/{id}/history", data.treatmentId())).andExpect(status().isOk());

        assertSingleEntry(reader, "READ", "Treatment", data.treatmentId());
    }

    @Test
    void changingTheResponsibleIsAuditedAsUpdate() throws Exception {
        ClinicalData data = createRecord();
        User other = createUser(Role.PHYSIOTHERAPIST);

        perform(other, put("/api/treatments/{id}/responsible", data.treatmentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"physiotherapistId\":%d}".formatted(other.getId())))
                .andExpect(status().isOk());

        assertSingleEntry(other, "UPDATE", "Treatment", data.treatmentId());
    }

    @Test
    void changingTheStatusIsAuditedAsStatusChange() throws Exception {
        ClinicalData data = createRecord();
        User other = createUser(Role.PHYSIOTHERAPIST);

        perform(other, patch("/api/treatments/{id}/status", data.treatmentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"COMPLETED\",\"reason\":\"Alta por mejoría\"}"))
                .andExpect(status().isOk());

        assertSingleEntry(other, "STATUS_CHANGE", "Treatment", data.treatmentId());
    }

    @Test
    void theClinicalSummaryOfThePatientListIsAuditedAsReadOfEachRecordShown() throws Exception {
        String surname = randomSurname();
        Long withRecord = TestPatients.insert(jdbc, "Ana", surname + " Uno");
        // Also listed, but without a record there is no clinical data to audit
        TestPatients.insert(jdbc, "Beatriz", surname + " Dos");
        ClinicalData data = createRecordFor(withRecord);
        User reader = createUser(Role.PHYSIOTHERAPIST);

        perform(reader, get("/api/physio/patients").param("q", surname)).andExpect(status().isOk());

        assertSingleEntry(reader, "READ", "ClinicalRecord", data.recordId());
        assertThat(entriesOf(reader)).hasSize(1);
    }

    @Test
    void rejectedOperationsAreNotAudited() throws Exception {
        ClinicalData data = createRecord();
        User other = createUser(Role.PHYSIOTHERAPIST);

        perform(other, get("/api/treatments/{id}", 999_999_999L)).andExpect(status().isNotFound());
        perform(other, get("/api/patients/{id}/clinical-record", 999_999_999L)).andExpect(status().isNotFound());
        perform(other, patch("/api/treatments/{id}/status", data.treatmentId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"newStatus\":\"ACTIVE\",\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableContent());
        perform(other, put("/api/patients/{id}/clinical-record", data.patientId())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(entriesOf(other)).isEmpty();
    }

    @Test
    void behindTheProxyTheClientIpComesFromXForwardedFor() throws Exception {
        ClinicalData data = createRecord();
        User reader = createUser(Role.PHYSIOTHERAPIST);

        perform(reader, get("/api/treatments/{id}", data.treatmentId())
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.12"))
                .andExpect(status().isOk());

        assertThat(entriesOf(reader)).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("ip_address", "203.0.113.7"));
    }

    // ----- helpers -----

    private record ClinicalData(Long patientId, Long recordId, Long treatmentId) {
    }

    private ClinicalData createRecord() throws Exception {
        return createRecordFor(TestPatients.insert(jdbc, "Juan Carlos", "Pérez Gómez"));
    }

    private ClinicalData createRecordFor(Long patientId) throws Exception {
        perform(physio, post("/api/patients/{id}/clinical-record", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(historyBody("No fuma").replace("}", ",\"treatment\":" + treatmentBody() + "}")))
                .andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);
        Long treatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
        return new ClinicalData(patientId, recordId, treatmentId);
    }

    /** Exactly one entry of the user for the action and entity, recent, with the local client IP. */
    private void assertSingleEntry(User user, String action, String entityType, Long entityId) {
        List<Map<String, Object>> entries = jdbc.queryForList("""
                SELECT occurred_at, ip_address FROM audit_logs
                WHERE user_id = ? AND action = ? AND entity_type = ? AND entity_id = ?""",
                user.getId(), action, entityType, entityId);
        assertThat(entries).as("%s %s %d by user %d", action, entityType, entityId, user.getId()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry).containsEntry("ip_address", LOCAL_IP);
                    Instant occurredAt = ((Timestamp) entry.get("occurred_at")).toInstant();
                    assertThat(occurredAt).isBetween(start, Instant.now().plus(Duration.ofSeconds(1)));
                });
    }

    private List<Map<String, Object>> entriesOf(User user) {
        return jdbc.queryForList("SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE user_id = ?",
                user.getId());
    }

    private ResultActions perform(User user,
            MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("auditoria-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de auditoría", role, null, Instant.now()));
    }

    /** A surname no other test uses, so the search returns only this test's patients. */
    private static String randomSurname() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder surname = new StringBuilder("Audit");
        for (int i = 0; i < 8; i++) {
            surname.append((char) ('a' + random.nextInt(26)));
        }
        return surname.toString();
    }

    private static String patientBody(String extraFields) {
        return """
                {%s"firstName":"Rosa Elena","lastName":"Ortiz Vega","birthDate":"1985-04-12","sex":"FEMALE",
                 "phone":"0991112233","email":"rosa.ortiz@correo.ec","address":"Av. 6 de Diciembre N34-12, Quito",
                 "occupation":"Contadora","educationLevel":"Superior","healthInsurance":"IESS"}"""
                .formatted(extraFields);
    }

    private static String historyBody(String habits) {
        return """
                {"habits":"%s","physicalActivity":"Camina 30 minutos","allergies":"Ninguna conocida",
                 "currentMedication":"Ibuprofeno 400 mg","personalHistory":"Hipertensión controlada",
                 "surgicalHistory":"Apendicectomía (2010)","familyHistory":"Madre con artrosis"}"""
                .formatted(habits);
    }

    private static String treatmentBody() {
        return """
                {"diagnosisCode":"M54.5","chiefComplaint":"Dolor lumbar al levantar peso",
                 "currentIllness":"Tres semanas de evolución","referringPhysician":"Dr. Carlos Andrade",
                 "complementaryTests":"Radiografía lumbar","severity":"MODERATE","painType":"Punzante",
                 "painLocation":"Región lumbar","initialPainScore":6,"painFrequency":"Intermitente",
                 "sessionFrequency":"3 veces por semana","goal":"Reducir el dolor","plannedSessions":10}""";
    }

}
