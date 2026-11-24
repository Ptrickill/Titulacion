package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.RecordingMailSender;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-02 end to end: "Crear acceso al portal" creates the PATIENT account from the patient's record, with its email
 * and its names, without typing them; the record then shows the account's status.
 */
@AutoConfigureMockMvc
class PortalAccountIntegrationTests extends AbstractIntegrationTest {

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

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Admin de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
    }

    @Test
    void createsTheAccountWithTheEmailAndTheNamesOfTheRecordWithoutTypingThem() throws Exception {
        String email = uniqueEmail("rosa");
        Long patientId = patientWithEmail("Rosa Elena", "Ortiz Vega", email);

        // No request body: nothing is typed
        String body = createPortalAccount(patientId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.emailChangePending").value(false))
                .andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();

        User account = userRepository.findById(userId).orElseThrow();
        assertThat(account.getEmail()).isEqualTo(email);
        assertThat(account.getFullName()).isEqualTo("Rosa Elena Ortiz Vega");
        assertThat(account.getRole()).isEqualTo(Role.PATIENT);
        assertThat(account.getPasswordHash()).isNull();
        assertThat(jdbc.queryForObject("SELECT portal_user_id FROM patients WHERE id = ?", Long.class, patientId))
                .isEqualTo(userId);
        assertThat(auditEntries("CREATE", "User", userId)).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", admin.getId()));
        assertThat(auditEntries("UPDATE", "Patient", patientId)).hasSize(1);
        assertThat(auditEntries("INVITATION_SENT", "User", userId)).hasSize(1);
        assertThat(mailSender.messagesTo(email)).singleElement().satisfies(message -> {
            assertThat(RecordingMailSender.subjectOf(message)).isEqualTo("Active su cuenta de Rehab Elite");
            assertThat(RecordingMailSender.plainText(message)).contains("Rosa Elena Ortiz Vega");
        });

        // The record shows the account and its status
        mockMvc.perform(get("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalAccount.userId").value(userId))
                .andExpect(jsonPath("$.portalAccount.status").value("PENDING_ACTIVATION"));

        // "Reenviar invitación" from the record uses the account's id
        mockMvc.perform(post("/api/users/{id}/resend-invitation", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(mailSender.messagesTo(email)).hasSize(2);
    }

    @Test
    void aPatientWithoutAccountShowsNoAccountAndOnlyOneAccountCanBeCreated() throws Exception {
        Long patientId = patientWithEmail("Carlos Andrés", "Vera Luna", uniqueEmail("carlos"));
        mockMvc.perform(get("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.portalAccount").doesNotExist());

        createPortalAccount(patientId).andExpect(status().isCreated());
        createPortalAccount(patientId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El paciente ya tiene una cuenta de acceso al portal."))
                .andExpect(jsonPath("$.existingUserId").exists());
    }

    @Test
    void aMailFailureAnswers503AndCreatesNothing() throws Exception {
        String email = uniqueEmail("sin-correo");
        Long patientId = patientWithEmail("Marta", "Salinas", email);
        mailSender.failFor(email);
        try {
            createPortalAccount(patientId)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail")
                            .value("No se pudo enviar el correo de activación. Intente nuevamente en unos minutos."));
        } finally {
            mailSender.stopFailingFor(email);
        }

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT portal_user_id FROM patients WHERE id = ?", Long.class, patientId))
                .isNull();
        assertThat(auditEntries("UPDATE", "Patient", patientId)).isEmpty();
    }

    @Test
    void anEmailThatAnotherAccountUsesIsAConflictAndAnUnknownPatientIsNotFound() throws Exception {
        String email = uniqueEmail("ocupado");
        userRepository.save(User.create(email, "not-a-real-hash", "Otra Cuenta", Role.PHYSIOTHERAPIST, null,
                Instant.now()));
        Long patientId = patientWithEmail("Elena", "Proaño", email);

        createPortalAccount(patientId).andExpect(status().isConflict());
        createPortalAccount(999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));
    }

    @Test
    void onlyTheAdministratorCreatesThePortalAccess() throws Exception {
        Long patientId = patientWithEmail("Elena", "Proaño", uniqueEmail("elena"));
        User physio = userRepository.save(User.create(uniqueEmail("fisio"), "not-a-real-hash", "Fisio",
                Role.PHYSIOTHERAPIST, null, Instant.now()));

        mockMvc.perform(post("/api/patients/{id}/portal-account", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(physio, Instant.now()).token()))
                .andExpect(status().isForbidden());
    }

    private Long patientWithEmail(String firstName, String lastName, String email) {
        Long patientId = TestPatients.insert(jdbc, firstName, lastName);
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", email, patientId);
        return patientId;
    }

    private ResultActions createPortalAccount(Long patientId) throws Exception {
        return mockMvc.perform(post("/api/patients/{id}/portal-account", patientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private List<Map<String, Object>> auditEntries(String action, String entityType, Long entityId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ?""",
                action, entityType, entityId);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
