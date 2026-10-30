package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * TEC-06: with the dev profile Flyway loads the synthetic seed and DevSeedPasswordInitializer gives the seed
 * accounts the password from DEV_SEED_PASSWORD. Runs against its own PostgreSQL container (not the one shared by
 * {@link AbstractIntegrationTest}), so the seed never reaches the other tests' database.
 */
@SpringBootTest(properties = "app.dev-seed.password=" + DevSeedIntegrationTests.SEED_PASSWORD)
@ActiveProfiles({"test", "dev"})
@AutoConfigureMockMvc
@Import(DevSeedIntegrationTests.OwnDatabase.class)
class DevSeedIntegrationTests {

    static final String SEED_PASSWORD = "Seed.Prueba2026";

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnDatabase {

        @Bean
        @ServiceConnection
        PostgreSQLContainer devSeedPostgres() {
            return new PostgreSQLContainer(AbstractIntegrationTest.POSTGRES_IMAGE);
        }

    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevSeedPasswordInitializer initializer;

    @Test
    void flywayAppliesTheSeedAsARepeatableMigration() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE script = 'R__dev_seed.sql' AND version IS NULL AND success""", Integer.class)).isEqualTo(1);
    }

    /** A database seeded once must still start without the dev profile (seed script not on the locations). */
    @Test
    void aSeededDatabaseValidatesWithoutTheDevProfile() {
        FluentConfiguration withoutDev = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration");

        assertThatThrownBy(() -> withoutDev.load().validate()).isInstanceOf(FlywayValidateException.class);
        assertThatCode(() -> withoutDev.ignoreMigrationPatterns("*:future", "repeatable:missing").load().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void seedsTwoPhysiotherapistsOneDirectorAndThreePatientAccounts() {
        assertThat(seedAccountsByRole()).containsOnly(
                Map.entry("PHYSIOTHERAPIST", 2L), Map.entry("OPERATIONS_DIRECTOR", 1L), Map.entry("PATIENT", 3L));
    }

    @Test
    void seedsFifteenPatientsAndLinksTheThreePortalAccounts() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM patients p JOIN users u ON u.id = p.portal_user_id
                WHERE u.role = 'PATIENT' AND u.full_name = p.first_name || ' ' || p.last_name""",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients WHERE national_id <> medical_record_number",
                Integer.class)).isZero();
        // Bootstrap data, not an action of a user: nothing is audited as created
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'CREATE'", Integer.class))
                .isZero();
    }

    @Test
    void seedAccountsLogInWithTheDevSeedPassword() throws Exception {
        assertThat(jdbc.queryForList("SELECT password_hash FROM users WHERE email LIKE '%@dev-seed.rehabelite.test'",
                String.class)).hasSize(6).allMatch(hash -> hash.startsWith("$2"));

        login("fisio.andrade@dev-seed.rehabelite.test", SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PHYSIOTHERAPIST"));
        login("paciente.ortiz@dev-seed.rehabelite.test", SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PATIENT"));
        login("direccion@dev-seed.rehabelite.test", "otra-clave-1")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runningTheSeedAndTheInitializerAgainChangesNothing() {
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        Integer patients = jdbc.queryForObject("SELECT count(*) FROM patients", Integer.class);
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'fisio.cevallos@dev-seed.rehabelite.test'", String.class);

        new ResourceDatabasePopulator(new ClassPathResource("db/dev-seed/R__dev_seed.sql")).execute(dataSource);
        initializer.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients", Integer.class)).isEqualTo(patients);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients WHERE portal_user_id IS NOT NULL",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'fisio.cevallos@dev-seed.rehabelite.test'",
                String.class)).isEqualTo(hash);
    }

    private Map<String, Long> seedAccountsByRole() {
        return jdbc.query("""
                SELECT role, count(*) AS total FROM users WHERE email LIKE '%@dev-seed.rehabelite.test'
                GROUP BY role""", rs -> {
            Map<String, Long> totals = new HashMap<>();
            while (rs.next()) {
                totals.put(rs.getString("role"), rs.getLong("total"));
            }
            return totals;
        });
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

}
