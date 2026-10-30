package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-02 end to end: the administrator creates accounts with a role through /api/users.
 */
@AutoConfigureMockMvc
class UserControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave.Inicial2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService tokenService;

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
    void createsAnActiveAccountWithABcryptPasswordThatCanLogIn() throws Exception {
        String email = uniqueEmail("fisio");

        String body = create(email.toUpperCase(), "Lucía Fernanda Mora", "PHYSIOTHERAPIST", null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/users/")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Lucía Fernanda Mora"))
                .andExpect(jsonPath("$.role").value("PHYSIOTHERAPIST"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        User created = userRepository.findById(id).orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getCreatedBy()).isEqualTo(admin.getId());
        assertThat(auditEntries("CREATE", "User", id)).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", admin.getId()));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PHYSIOTHERAPIST"));
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegisteredIgnoringCase() throws Exception {
        String email = uniqueEmail("duplicado");
        create(email, "Primera Cuenta", "OPERATIONS_DIRECTOR", null).andExpect(status().isCreated());

        create(email.toUpperCase(), "Segunda Cuenta", "PHYSIOTHERAPIST", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Ya existe un usuario registrado con ese correo electrónico."));

        assertThat(userRepository.findByEmail(email)).get().extracting(User::getFullName).isEqualTo("Primera Cuenta");
    }

    @Test
    void patientAccountRequiresARegisteredPatient() throws Exception {
        create(uniqueEmail("paciente"), "Rosa Ortiz", "PATIENT", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La cuenta de paciente debe vincularse a un paciente registrado."));

        String email = uniqueEmail("paciente");
        create(email, "Rosa Ortiz", "PATIENT", 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente seleccionado no existe."));
        // The failed link rolls back the account as well
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void createsAPatientAccountLinkedToThePatient() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");

        String body = create(uniqueEmail("paciente"), "Rosa Elena Ortiz Vega", "PATIENT", patientId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(body, "$.id")).longValue();

        assertThat(jdbc.queryForObject("SELECT portal_user_id FROM patients WHERE id = ?", Long.class, patientId))
                .isEqualTo(userId);
        assertThat(auditEntries("CREATE", "User", userId)).hasSize(1);
        assertThat(auditEntries("UPDATE", "Patient", patientId)).hasSize(1);
    }

    @Test
    void aPatientCanHaveOnlyOnePortalAccount() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Carlos Andrés", "Vera Luna");
        create(uniqueEmail("paciente"), "Carlos Vera", "PATIENT", patientId).andExpect(status().isCreated());

        String second = uniqueEmail("paciente");
        create(second, "Carlos Vera", "PATIENT", patientId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El paciente seleccionado ya tiene una cuenta de portal."));
        assertThat(userRepository.findByEmail(second)).isEmpty();
    }

    @Test
    void onlyPatientAccountsCanBeLinked() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Marta", "Salinas");

        create(uniqueEmail("fisio"), "Marta Salinas", "PHYSIOTHERAPIST", patientId)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void invalidDataIsRejectedWithFieldErrorsInSpanish() throws Exception {
        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\",\"fullName\":\"\",\"role\":null,\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
                        "email", "fullName", "role", "password", "password")));

        create(uniqueEmail("x"), "Sin Numeros", "PHYSIOTHERAPIST", null, "SoloLetrasLargas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("debe contener al menos una letra y un número"));
    }

    @Test
    void listsAccountsWithoutPasswordHashes() throws Exception {
        String email = uniqueEmail("listado");
        create(email, "Zoila Listado", "OPERATIONS_DIRECTOR", null).andExpect(status().isCreated());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(hasItem(email)))
                .andExpect(jsonPath("$[*].passwordHash").isEmpty())
                .andExpect(jsonPath("$[0].role").exists())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString())
                        .doesNotContain("$2a$").doesNotContain("$2b$"));
    }

    @Test
    void onlyTheAdministratorManagesAccounts() throws Exception {
        User physio = userRepository.save(User.create(uniqueEmail("fisio"), "not-a-real-hash", "Fisio",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        String token = tokenService.issue(physio, Instant.now()).token();

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueEmail("x"), "X", "ADMINISTRATOR", null, PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String email, String fullName, String role, Long patientId) throws Exception {
        return create(email, fullName, role, patientId, PASSWORD);
    }

    private ResultActions create(String email, String fullName, String role, Long patientId, String password)
            throws Exception {
        return mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, fullName, role, patientId, password)));
    }

    private static String json(String email, String fullName, String role, Long patientId, String password) {
        return """
                {"email":"%s","fullName":"%s","role":"%s","password":"%s","patientId":%s}"""
                .formatted(email, fullName, role, password, patientId);
    }

    private List<Map<String, Object>> auditEntries(String action, String entityType, Long entityId) {
        return jdbc.queryForList("""
                SELECT user_id, ip_address FROM audit_logs
                WHERE action = ? AND entity_type = ? AND entity_id = ?""", action, entityType, entityId);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
