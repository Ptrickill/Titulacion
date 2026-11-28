package ec.rehabelite.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
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
 * MEJ-06 (D15): in the portal the patient sees its own consents, accepts the texts in force and revokes the email
 * communications; the consent to process the data is revoked only at the reception. The patient is always the one of
 * the account.
 */
@AutoConfigureMockMvc
class PortalConsentIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User account;
    private Long patientId;

    @BeforeEach
    void setUp() {
        account = userRepository.save(User.create("portal-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Paciente del portal", Role.PATIENT, null, Instant.now()));
        patientId = TestPatients.insert(jdbc, "Paciente", "Del Portal");
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", account.getId(), patientId);
    }

    @Test
    void thePatientAcceptsAndRevokesTheEmailCommunications() throws Exception {
        perform(get("/api/portal/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DATA_PROCESSING"))
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andExpect(jsonPath("$[1].type").value("EMAIL_COMMUNICATIONS"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));

        accept("EMAIL_COMMUNICATIONS", TestPatients.EMAIL_COMMUNICATIONS_VERSION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].status").value("CURRENT"))
                .andExpect(jsonPath("$[1].channel").value("PORTAL"));
        perform(post("/api/portal/consents/EMAIL_COMMUNICATIONS/revocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].status").value("REVOKED"))
                .andExpect(jsonPath("$[1].revocationChannel").value("PORTAL"));

        assertThat(jdbc.queryForList("""
                SELECT a.action FROM audit_logs a JOIN patient_consents c ON c.id = a.entity_id
                WHERE a.entity_type = 'PatientConsent' AND a.user_id = ? AND c.patient_id = ? ORDER BY a.id""",
                String.class, account.getId(), patientId)).containsExactly("CREATE", "STATUS_CHANGE");
    }

    @Test
    void theDataConsentIsRevokedOnlyAtTheReceptionAndTheTextMustBeTheOneInForce() throws Exception {
        perform(post("/api/portal/consents/DATA_PROCESSING/revocation"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Para revocar el consentimiento del tratamiento de sus datos, "
                        + "solicítelo en la recepción de la clínica."));
        accept("EMAIL_COMMUNICATIONS", "0.0-anterior")
                .andExpect(status().isUnprocessableContent());
        accept("DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene vigente ese consentimiento."));
    }

    @Test
    void anEarlierVersionIsReplacedWhenThePatientAcceptsTheNewOne() throws Exception {
        jdbc.update("UPDATE patient_consents SET text_version = '0.0-anterior' WHERE patient_id = ?", patientId);
        perform(get("/api/portal/consents")).andExpect(jsonPath("$[0].status").value("PREVIOUS_VERSION"));

        accept("DATA_PROCESSING", TestPatients.DATA_PROCESSING_VERSION)
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andExpect(jsonPath("$[0].channel").value("PORTAL"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_consents WHERE patient_id = ?", Integer.class,
                patientId)).isEqualTo(2);
    }

    @Test
    void anAccountWithoutPatientHasNoConsents() throws Exception {
        User alone = userRepository.save(User.create("sin-paciente-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Sin paciente", Role.PATIENT, null, Instant.now()));
        mockMvc.perform(get("/api/portal/consents").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenService.issue(alone, Instant.now()).token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La cuenta no tiene un paciente vinculado."));
    }

    private ResultActions accept(String type, String version) throws Exception {
        return perform(post("/api/portal/consents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"version\":\"%s\"}".formatted(type, version)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(account, Instant.now()).token()));
    }

}
