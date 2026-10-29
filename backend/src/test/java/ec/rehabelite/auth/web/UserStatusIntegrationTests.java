package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.InvalidCredentialsException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-03 end to end: the administrator activates and deactivates accounts through PATCH /api/users/{id}/status.
 */
@AutoConfigureMockMvc
class UserStatusIntegrationTests extends AbstractIntegrationTest {

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
        admin = createUser(Role.ADMINISTRATOR);
        adminToken = tokenOf(admin);
    }

    @Test
    void deactivatedAccountCannotLogInAndTheChangeIsAudited() throws Exception {
        User physio = createUser(Role.PHYSIOTHERAPIST);

        changeStatus(adminToken, physio.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(physio.getId()))
                .andExpect(jsonPath("$.active").value(false));

        login(physio.getEmail())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(InvalidCredentialsException.USER_MESSAGE));
        User stored = userRepository.findById(physio.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getUpdatedBy()).isEqualTo(admin.getId());
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(statusChangeAuthors(physio.getId())).containsExactly(admin.getId());
    }

    @Test
    void reactivatedAccountCanLogInAgain() throws Exception {
        User director = createUser(Role.OPERATIONS_DIRECTOR);
        changeStatus(adminToken, director.getId(), false).andExpect(status().isOk());
        login(director.getEmail()).andExpect(status().isUnauthorized());

        changeStatus(adminToken, director.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        login(director.getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OPERATIONS_DIRECTOR"));
        assertThat(statusChangeAuthors(director.getId())).containsExactly(admin.getId(), admin.getId());
    }

    @Test
    void tokensIssuedBeforeTheDeactivationStopWorking() throws Exception {
        User otherAdmin = createUser(Role.ADMINISTRATOR);
        String otherToken = tokenOf(otherAdmin);
        listUsers(otherToken).andExpect(status().isOk());

        changeStatus(adminToken, otherAdmin.getId(), false).andExpect(status().isOk());

        listUsers(otherToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Debe iniciar sesión para acceder a este recurso."));
        changeStatus(adminToken, otherAdmin.getId(), true).andExpect(status().isOk());
        listUsers(otherToken).andExpect(status().isOk());
    }

    @Test
    void administratorCannotDeactivateTheirOwnAccount() throws Exception {
        changeStatus(adminToken, admin.getId(), false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("No puede desactivar su propia cuenta."));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
        assertThat(statusChangeAuthors(admin.getId())).isEmpty();
        listUsers(adminToken).andExpect(status().isOk());
    }

    @Test
    void askingForTheCurrentStatusIsNotAudited() throws Exception {
        User physio = createUser(Role.PHYSIOTHERAPIST);

        changeStatus(adminToken, physio.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(statusChangeAuthors(physio.getId())).isEmpty();
    }

    @Test
    void unknownAccountOrMissingStatusIsRejected() throws Exception {
        changeStatus(adminToken, 999_999_999L, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El usuario no existe."));

        User physio = createUser(Role.PHYSIOTHERAPIST);
        mockMvc.perform(patch("/api/users/{id}/status", physio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("active"));
        assertThat(userRepository.findById(physio.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void onlyTheAdministratorChangesTheStatus() throws Exception {
        User target = createUser(Role.PATIENT);

        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            changeStatus(tokenOf(createUser(role)), target.getId(), false).andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/users/{id}/status", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
        assertThat(statusChangeAuthors(target.getId())).isEmpty();
    }

    @Test
    void openApiDocumentsTheStatusEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/{id}/status'].patch.tags[0]").value("Usuarios"))
                .andExpect(jsonPath("$.paths['/api/users/{id}/status'].patch.responses['422']").exists());
    }

    private ResultActions changeStatus(String token, Long userId, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":%s}".formatted(active)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
    }

    private ResultActions listUsers(String token) throws Exception {
        return mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<Long> statusChangeAuthors(Long userId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs
                WHERE action = 'STATUS_CHANGE' AND entity_type = 'User' AND entity_id = ?
                ORDER BY id""", Long.class, userId);
    }

    private User createUser(Role role) {
        String email = "estado-" + UUID.randomUUID() + "@rehabelite.test";
        return userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Usuario de prueba", role,
                null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
