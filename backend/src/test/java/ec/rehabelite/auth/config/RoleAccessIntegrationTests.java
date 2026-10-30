package ec.rehabelite.auth.config;

import static ec.rehabelite.auth.Role.ADMINISTRATOR;
import static ec.rehabelite.auth.Role.OPERATIONS_DIRECTOR;
import static ec.rehabelite.auth.Role.PATIENT;
import static ec.rehabelite.auth.Role.PHYSIOTHERAPIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-04: every API area is called with each role and without a token. Allowed roles get past security (the
 * areas have no controllers yet, so they answer 404 until their stories add them); other roles get 403 and an
 * ACCESS_DENIED audit entry; anonymous calls get 401 and are not audited.
 */
@AutoConfigureMockMvc
class RoleAccessIntegrationTests extends AbstractIntegrationTest {

    /** Expected access matrix, written independently of {@link RoleAccessRules}. */
    record Area(String path, String entityType, Long entityId, Set<Role> allowed) {

        Area(String path, String entityType, Long entityId, Role... allowed) {
            this(path, entityType, entityId, Set.of(allowed));
        }

        @Override
        public String toString() {
            return path;
        }

    }

    private static final List<Area> AREAS = List.of(
            new Area("/api/admin/settings", "admin", null, ADMINISTRATOR),
            new Area("/api/users/5", "users", 5L, ADMINISTRATOR),
            new Area("/api/patients/portal-candidates", "patients", null, ADMINISTRATOR),
            new Area("/api/patients/12", "patients", 12L, ADMINISTRATOR, PHYSIOTHERAPIST),
            new Area("/api/clinical-records/7", "clinical-records", 7L, PHYSIOTHERAPIST),
            new Area("/api/treatments/3/status-changes", "treatments", 3L, PHYSIOTHERAPIST),
            new Area("/api/therapy-sessions", "therapy-sessions", null, PHYSIOTHERAPIST),
            new Area("/api/ai-summaries/9", "ai-summaries", 9L, PHYSIOTHERAPIST),
            new Area("/api/reports/treatments", "reports", null, OPERATIONS_DIRECTOR, ADMINISTRATOR),
            new Area("/api/audit-logs", "audit-logs", null, OPERATIONS_DIRECTOR, ADMINISTRATOR),
            new Area("/api/portal/treatment", "portal", null, PATIENT));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    static Stream<Arguments> areaAndRole() {
        return AREAS.stream().flatMap(area -> Arrays.stream(Role.values()).map(role -> Arguments.of(area, role)));
    }

    @ParameterizedTest(name = "{1} → {0}")
    @MethodSource("areaAndRole")
    void eachRoleOnlyReachesItsOwnAreas(Area area, Role role) throws Exception {
        User user = createUser(role);
        String token = tokenService.issue(user, Instant.now()).token();

        var result = mockMvc.perform(get(area.path()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        if (area.allowed().contains(role)) {
            int status = result.andReturn().getResponse().getStatus();
            assertThat(status).as("allowed role must pass security").isNotIn(401, 403);
            assertThat(accessDeniedEntries(user)).isEmpty();
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Acceso denegado"))
                    .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.instance").value(area.path()));
            assertThat(accessDeniedEntries(user)).singleElement().satisfies(entry -> assertThat(entry)
                    .containsEntry("entity_type", area.entityType())
                    .containsEntry("entity_id", area.entityId())
                    .containsEntry("ip_address", "127.0.0.1"));
        }
    }

    @ParameterizedTest(name = "sin token → {0}")
    @MethodSource("areas")
    void anonymousRequestsGet401AndAreNotAudited(Area area) throws Exception {
        Integer before = anonymousAccessDeniedCount();

        mockMvc.perform(get(area.path()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").value("Debe iniciar sesión para acceder a este recurso."));

        assertThat(anonymousAccessDeniedCount()).isEqualTo(before);
    }

    static Stream<Area> areas() {
        return AREAS.stream();
    }

    /** Physiotherapists look patients up (GET, in the matrix above) but only the administrator registers them. */
    @ParameterizedTest(name = "POST /api/patients → {0}")
    @MethodSource("roles")
    void onlyTheAdministratorRegistersPatients(Role role) throws Exception {
        User user = createUser(role);
        String token = tokenService.issue(user, Instant.now()).token();

        var result = mockMvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        if (role == ADMINISTRATOR) {
            result.andExpect(status().isBadRequest());
            assertThat(accessDeniedEntries(user)).isEmpty();
        } else {
            result.andExpect(status().isForbidden());
            assertThat(accessDeniedEntries(user)).singleElement()
                    .satisfies(entry -> assertThat(entry).containsEntry("entity_type", "patients"));
        }
    }

    /** Physiotherapists look patients up, but only the administrator updates their data (HU-06). */
    @ParameterizedTest(name = "PUT /api/patients/12 → {0}")
    @MethodSource("roles")
    void onlyTheAdministratorUpdatesPatients(Role role) throws Exception {
        User user = createUser(role);
        String token = tokenService.issue(user, Instant.now()).token();

        var result = mockMvc.perform(put("/api/patients/12").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        if (role == ADMINISTRATOR) {
            result.andExpect(status().isBadRequest());
            assertThat(accessDeniedEntries(user)).isEmpty();
        } else {
            result.andExpect(status().isForbidden());
            assertThat(accessDeniedEntries(user)).singleElement().satisfies(entry -> assertThat(entry)
                    .containsEntry("entity_type", "patients")
                    .containsEntry("entity_id", 12L));
        }
    }

    static Stream<Role> roles() {
        return Arrays.stream(Role.values());
    }

    @Test
    void expiredOrTamperedTokensGet401() throws Exception {
        User user = createUser(ADMINISTRATOR);
        String expired = tokenService.issue(user, Instant.now().minus(Duration.ofHours(2))).token();
        String valid = tokenService.issue(user, Instant.now()).token();
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("A") ? "BB" : "AA");

        for (String token : List.of(expired, tampered, "not-a-jwt")) {
            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("No autenticado"));
        }
        assertThat(accessDeniedEntries(user)).isEmpty();
    }

    @Test
    void endpointsOutsideTheAreasAreOpenToEveryAuthenticatedRole() throws Exception {
        for (Role role : Role.values()) {
            String token = tokenService.issue(createUser(role), Instant.now()).token();
            int status = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(role.name()).isNotIn(401, 403);
        }
    }

    @Test
    void loginStaysPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie-" + UUID.randomUUID() + "@rehabelite.test\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Inicio de sesión fallido"));
    }

    private User createUser(Role role) {
        // Tokens are issued directly, so the password hash is never checked
        return userRepository.save(User.create("rol-" + UUID.randomUUID() + "@rehabelite.test", "not-a-real-hash",
                "Usuario de prueba", role, null, Instant.now()));
    }

    private List<Map<String, Object>> accessDeniedEntries(User user) {
        return jdbc.queryForList("""
                SELECT entity_type, entity_id, ip_address FROM audit_logs
                WHERE user_id = ? AND action = 'ACCESS_DENIED'""", user.getId());
    }

    private Integer anonymousAccessDeniedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id IS NULL AND action = 'ACCESS_DENIED'",
                Integer.class);
    }

}
