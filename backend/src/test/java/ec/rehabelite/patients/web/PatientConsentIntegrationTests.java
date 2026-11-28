package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.RecordingMailSender;
import ec.rehabelite.TestClinicalRecords;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MEJ-06 (B1): the consent to process the data is required to register a patient and, for any patient, to receive new
 * care and portal access; revoking it closes the portal and blocks new care while what was recorded stays readable.
 * The account emails do not depend on the consent to email communications (D14).
 */
@AutoConfigureMockMvc
class PatientConsentIntegrationTests extends AbstractIntegrationTest {

    private static final String NO_CONSENT = "El paciente no tiene un consentimiento vigente para el tratamiento de "
            + "sus datos. Regístrelo en su ficha.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private User reception;
    private User physio;

    @BeforeEach
    void setUp() {
        reception = createUser(Role.ADMINISTRATOR);
        physio = createUser(Role.PHYSIOTHERAPIST);
    }

    @Test
    void registeringAPatientRequiresTheConsentToProcessTheDataForTheTextInForce() throws Exception {
        String withoutConsent = registrationBody(TestPatients.randomNationalId(), null, null);
        perform(reception, post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(withoutConsent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dataProcessingConsentVersion"))
                .andExpect(jsonPath("$.errors[0].message").value("Registre el consentimiento firmado del paciente "
                        + "para el tratamiento de sus datos personales y de salud."));

        String outdated = registrationBody(TestPatients.randomNationalId(), "0.0-anterior", null);
        perform(reception, post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(outdated))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El texto del consentimiento cambió. Revíselo de nuevo con el "
                        + "paciente antes de registrarlo."));

        String nationalId = TestPatients.randomNationalId();
        String body = perform(reception, post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(nationalId, TestPatients.DATA_PROCESSING_VERSION,
                                TestPatients.EMAIL_COMMUNICATIONS_VERSION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentPending").value(false))
                .andExpect(jsonPath("$.blocked").value(false))
                .andReturn().getResponse().getContentAsString();
        Long patientId = ((Number) JsonPath.read(body, "$.id")).longValue();

        consents(patientId)
                .andExpect(jsonPath("$.consents[0].type").value("DATA_PROCESSING"))
                .andExpect(jsonPath("$.consents[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.consents[0].channel").value("PHYSICAL_SIGNATURE"))
                .andExpect(jsonPath("$.consents[1].type").value("EMAIL_COMMUNICATIONS"))
                .andExpect(jsonPath("$.consents[1].status").value("CURRENT"))
                .andExpect(jsonPath("$.history.length()").value(2));
        assertThat(jdbc.queryForList("""
                SELECT a.user_id FROM audit_logs a JOIN patient_consents c ON c.id = a.entity_id
                WHERE a.action = 'CREATE' AND a.entity_type = 'PatientConsent' AND c.patient_id = ?""",
                Long.class, patientId)).containsExactly(reception.getId(), reception.getId());
    }

    @Test
    void aPatientWithoutConsentGetsNoNewCareNorPortalUntilTheReceptionRecordsIt() throws Exception {
        Long patientId = TestPatients.insertWithoutConsent(jdbc, "Elena", "Proaño Sotomayor");

        perform(reception, get("/api/patients/{id}", patientId)).andExpect(jsonPath("$.consentPending").value(true));
        consents(patientId).andExpect(jsonPath("$.consents[0].status").value("PENDING"))
                .andExpect(jsonPath("$.consents[1].status").value("PENDING"))
                .andExpect(jsonPath("$.history.length()").value(0));
        createRecord(patientId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(NO_CONSENT));
        perform(reception, post("/api/patients/{id}/portal-account", patientId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(NO_CONSENT));

        grant(patientId, "DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].status").value("CURRENT"));
        createRecord(patientId).andExpect(status().isCreated());
        perform(reception, get("/api/patients/{id}", patientId)).andExpect(jsonPath("$.consentPending").value(false));
    }

    @Test
    void portalAccessNeedsOnlyTheDataConsentAndItsAccountEmailsAlwaysLeave() throws Exception {
        // D14: without EMAIL_COMMUNICATIONS the activation link still goes, it is an account email
        String email = "consentimiento-" + UUID.randomUUID() + "@rehabelite.test";
        Long patientId = TestPatients.insert(jdbc, "Rosa", "Ortiz");
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", email, patientId);

        perform(reception, post("/api/patients/{id}/portal-account", patientId)).andExpect(status().isCreated());

        assertThat(mailSender.messagesTo(email)).singleElement().satisfies(message ->
                assertThat(RecordingMailSender.subjectOf(message)).isEqualTo("Active su cuenta de Rehab Elite"));
        consents(patientId).andExpect(jsonPath("$.consents[1].status").value("PENDING"));
    }

    @Test
    void revokingTheDataConsentClosesThePortalAndBlocksNewCareButKeepsTheRecordReadable() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Luis", "Quishpe");
        createRecord(patientId).andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);
        Long treatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
        String body = perform(reception, post("/api/patients/{id}/portal-account", patientId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();

        perform(reception, post("/api/patients/{id}/consents/DATA_PROCESSING/revocation", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.consents[0].revocationChannel").value("PHYSICAL_SIGNATURE"));

        assertThat(userRepository.findById(userId).orElseThrow().isActive()).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'STATUS_CHANGE' AND entity_type = 'User'
                AND entity_id = ? AND user_id = ?""", Integer.class, userId, reception.getId())).isEqualTo(1);
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(5)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(NO_CONSENT));
        perform(physio, post("/api/clinical-records/{id}/treatments", recordId)
                .contentType(MediaType.APPLICATION_JSON).content(TestClinicalRecords.TREATMENT))
                .andExpect(status().isUnprocessableContent());
        // What was recorded stays readable (conservation of the clinical record)
        perform(physio, get("/api/patients/{id}/clinical-record", patientId)).andExpect(status().isOk());
        // The portal account does not come back without a new consent
        perform(reception, patch("/api/users/{id}/status", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(NO_CONSENT));

        grant(patientId, "DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION).andExpect(status().isOk());
        perform(reception, patch("/api/users/{id}/status", userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());
        perform(reception, post("/api/patients/{id}/consents/DATA_PROCESSING/revocation", patientId))
                .andExpect(status().isOk());
        perform(reception, post("/api/patients/{id}/consents/DATA_PROCESSING/revocation", patientId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente no tiene vigente ese consentimiento."));
    }

    @Test
    void aConsentOfAnEarlierVersionStaysValidUntilTheNewOneReplacesIt() throws Exception {
        Long patientId = TestPatients.insertWithoutConsent(jdbc, "Jorge", "Salazar");
        jdbc.update("""
                INSERT INTO patient_consents (patient_id, consent_type, text_version, channel, granted_at)
                VALUES (?, 'DATA_PROCESSING', '0.0-anterior', 'PHYSICAL_SIGNATURE', now() - interval '30 days')""",
                patientId);

        consents(patientId).andExpect(jsonPath("$.consents[0].status").value("PREVIOUS_VERSION"))
                .andExpect(jsonPath("$.consents[0].textVersion").value("0.0-anterior"))
                .andExpect(jsonPath("$.consents[0].currentTextVersion").value(TestPatients.DATA_PROCESSING_VERSION));
        perform(reception, get("/api/patients/{id}", patientId)).andExpect(jsonPath("$.consentPending").value(false));
        createRecord(patientId).andExpect(status().isCreated());

        grant(patientId, "DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION)
                .andExpect(jsonPath("$.consents[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].replacedAt").isNotEmpty());
        grant(patientId, "DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene vigente ese consentimiento."));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM patient_consents WHERE patient_id = ? AND revoked_at IS NULL
                AND replaced_at IS NULL""", Integer.class, patientId)).isEqualTo(1);
    }

    @Test
    void onlyTheReceptionManagesTheConsentsOfAPatient() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Ana", "Mendoza");
        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role);
            perform(user, get("/api/patients/{id}/consents", patientId)).andExpect(status().isForbidden());
            perform(user, post("/api/patients/{id}/consents/EMAIL_COMMUNICATIONS/revocation", patientId))
                    .andExpect(status().isForbidden());
        }
        consents(999_999_999L).andExpect(status().isNotFound());
    }

    private ResultActions consents(Long patientId) throws Exception {
        return perform(reception, get("/api/patients/{id}/consents", patientId));
    }

    private ResultActions grant(Long patientId, String type, String version) throws Exception {
        return perform(reception, post("/api/patients/{id}/consents", patientId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"version\":\"%s\"}".formatted(type, version)));
    }

    private ResultActions createRecord(Long patientId) throws Exception {
        return perform(physio, post("/api/patients/{id}/clinical-record", patientId)
                .contentType(MediaType.APPLICATION_JSON).content(TestClinicalRecords.record("No fuma")));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("consentimientos-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de consentimientos", role, null, Instant.now()));
    }

    private static String registrationBody(String nationalId, String dataVersion, String emailVersion) {
        String consents = (dataVersion == null ? "" : ",\"dataProcessingConsentVersion\":\"" + dataVersion + "\"")
                + (emailVersion == null ? "" : ",\"emailCommunicationsConsentVersion\":\"" + emailVersion + "\"");
        return """
                {"nationalId":"%s","firstName":"Rosa Elena","lastName":"Ortiz Vega","birthDate":"1985-04-12",
                 "sex":"FEMALE","phone":"0991112233","email":"rosa.%s@correo.ec",
                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Av. Ilaló",
                            "crossStreet":"Río Pita","reference":"Frente al parque"},
                 "occupation":"Contadora","educationLevel":"Superior","hasHealthInsurance":true%s}"""
                .formatted(nationalId, UUID.randomUUID(), consents);
    }

}
