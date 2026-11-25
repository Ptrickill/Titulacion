package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * MEJ-05: the test profile sets app.initial-director and the test database starts without directors, so the initial
 * operations director is created when the context starts: activated, with a BCrypt hash and its staff record.
 */
@AutoConfigureMockMvc
class DirectorAccountInitializerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private DirectorAccountInitializer initializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void createsTheConfiguredOperationsDirectorActivatedAndWithABcryptHash() {
        User director = userRepository.findByEmail(User.normalizeEmail(properties.initialDirector().email()))
                .orElseThrow();

        assertThat(director.getRole()).isEqualTo(Role.OPERATIONS_DIRECTOR);
        assertThat(director.isActive()).isTrue();
        assertThat(director.isPendingActivation()).isFalse();
        assertThat(director.getFullName()).isEqualTo(DirectorAccountInitializer.DIRECTOR_FULL_NAME);
        assertThat(director.getPasswordHash()).startsWith("$2").isNotEqualTo(properties.initialDirector().password());
        assertThat(passwordEncoder.matches(properties.initialDirector().password(), director.getPasswordHash()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_records WHERE user_id = ?", Integer.class,
                director.getId())).isEqualTo(1);
    }

    @Test
    void theInitialDirectorLogsInAsOperationsDirector() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", properties.initialDirector().email(),
                                "password", properties.initialDirector().password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OPERATIONS_DIRECTOR"));
    }

    @Test
    void doesNothingWhenAnActiveOperationsDirectorAlreadyExists() {
        long before = userRepository.count();

        initializer.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(before);
    }

}
