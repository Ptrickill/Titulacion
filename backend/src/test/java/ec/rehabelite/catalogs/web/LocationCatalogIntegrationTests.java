package ec.rehabelite.catalogs.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** MEJ-03: GET /api/catalogs/locations for the address lists. */
@AutoConfigureMockMvc
class LocationCatalogIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyAuthenticatedRoleGetsTheWholeCatalogWithOfficialAndDisplayNames() throws Exception {
        for (Role role : Role.values()) {
            mockMvc.perform(get("/api/catalogs/locations").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                    .andExpect(jsonPath("$.version").value("CGE 2025"))
                    .andExpect(jsonPath("$.cutoffDate").value("2024-12-31"))
                    .andExpect(jsonPath("$.provinces.length()").value(24))
                    .andExpect(jsonPath("$.provinces[?(@.code == '17')].name").value("PICHINCHA"))
                    .andExpect(jsonPath("$.provinces[?(@.code == '17')].displayName").value("Pichincha"))
                    .andExpect(jsonPath("$.provinces[?(@.code == '17')].cantons[?(@.code == '1707')].displayName")
                            .value("San Miguel de los Bancos"))
                    .andExpect(jsonPath("$.provinces[?(@.code == '17')].cantons[?(@.code == '1701')]"
                            + ".parishes[?(@.code == '170156')].type").value("RURAL"));
        }
    }

    @Test
    void needsASessionAndIsNotAudited() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);

        mockMvc.perform(get("/api/catalogs/locations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/catalogs/locations").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMINISTRATOR)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class))
                .isEqualTo(before);
    }

    private String bearer(Role role) {
        User user = userRepository.save(User.create("catalogo-" + UUID.randomUUID() + "@rehabelite.test", "hash",
                "Usuario", role, null, Instant.now()));
        return "Bearer " + tokenService.issue(user, Instant.now()).token();
    }

}
