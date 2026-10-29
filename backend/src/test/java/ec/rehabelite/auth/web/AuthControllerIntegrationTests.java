package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.InvalidCredentialsException;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/auth/login end to end: HTTP, security filter chain, PostgreSQL and the audit log.
 */
@AutoConfigureMockMvc
class AuthControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave.Segura2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void validCredentialsReturnAJwtWithTheUserClaims() throws Exception {
        User user = createUser(Role.PHYSIOTHERAPIST);

        String body = login(user.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.fullName").value("Paula Cevallos"))
                .andExpect(jsonPath("$.user.role").value("PHYSIOTHERAPIST"))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.token"));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(user.getId()));
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getClaimAsString("fullName")).isEqualTo("Paula Cevallos");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("PHYSIOTHERAPIST");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void successfulLoginUpdatesLastLoginAndIsAudited() throws Exception {
        User user = createUser(Role.ADMINISTRATOR);

        login(user.getEmail(), PASSWORD).andExpect(status().isOk());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
        Map<String, Object> entry = jdbc.queryForMap(
                "SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE user_id = ?", user.getId());
        assertThat(entry).containsEntry("action", "LOGIN")
                .containsEntry("entity_type", "User")
                .containsEntry("entity_id", user.getId())
                .containsEntry("ip_address", "127.0.0.1");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGenericMessage() throws Exception {
        User user = createUser(Role.PATIENT);

        expectGenericFailure(login(user.getEmail(), "Clave.Incorrecta"));
        expectGenericFailure(login("nadie-" + UUID.randomUUID() + "@rehabelite.test", PASSWORD));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedAttempts()).isEqualTo(1);
        assertThat(loginAuditCount(user)).isZero();
    }

    @Test
    void sixthAttemptIsBlockedEvenWithTheRightPassword() throws Exception {
        User user = createUser(Role.OPERATIONS_DIRECTOR);

        for (int attempt = 1; attempt <= User.MAX_FAILED_ATTEMPTS; attempt++) {
            expectGenericFailure(login(user.getEmail(), "Clave.Incorrecta"));
        }
        User locked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getFailedAttempts()).isEqualTo(User.MAX_FAILED_ATTEMPTS);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));

        expectGenericFailure(login(user.getEmail(), PASSWORD));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNull();
        assertThat(loginAuditCount(user)).isZero();
    }

    @Test
    void invalidRequestBodyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void protectedEndpointsRequireTheToken() throws Exception {
        User user = createUser(Role.PHYSIOTHERAPIST);
        String body = login(user.getEmail(), PASSWORD).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.title").value("No autenticado"));

        mockMvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());

        // Authenticated: the request passes security and reaches MVC (no controller yet, hence 404)
        mockMvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentsTheLoginEndpointPublicly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.tags[0]").value("Autenticación"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private static void expectGenericFailure(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Inicio de sesión fallido"))
                .andExpect(jsonPath("$.detail").value(InvalidCredentialsException.USER_MESSAGE));
    }

    private User createUser(Role role) {
        String email = "usuario-" + UUID.randomUUID() + "@rehabelite.test";
        return userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Paula Cevallos", role, null,
                Instant.now()));
    }

    private Integer loginAuditCount(User user) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'LOGIN'",
                Integer.class, user.getId());
    }

}
