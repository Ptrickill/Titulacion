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
    void seedsThreePhysiotherapistsOneDirectorAndThreePatientAccounts() {
        assertThat(seedAccountsByRole()).containsOnly(
                Map.entry("PHYSIOTHERAPIST", 3L), Map.entry("OPERATIONS_DIRECTOR", 1L), Map.entry("PATIENT", 3L));
    }

    /**
     * MEJ-04: every seed physiotherapist has a schedule, Paredes a partial one, and their loads differ (4, 2 and 1
     * active treatments); every seed treatment has its responsible and no record has two active treatments.
     */
    @Test
    void seedsSchedulesAndTreatmentsWithDifferentLoads() {
        assertThat(jdbc.queryForList("""
                SELECT u.email || ':' || count(*) FROM staff_schedule_slots s
                JOIN staff_records r ON r.id = s.staff_record_id JOIN users u ON u.id = r.user_id
                GROUP BY u.email ORDER BY u.email""", String.class)).containsExactly(
                "fisio.andrade@dev-seed.rehabelite.test:10", "fisio.cevallos@dev-seed.rehabelite.test:4",
                "fisio.paredes@dev-seed.rehabelite.test:2");
        assertThat(jdbc.queryForList("""
                SELECT u.email || ':' || count(*) FROM treatments t
                JOIN responsible_assignments a ON a.treatment_id = t.id AND a.valid_to IS NULL
                JOIN users u ON u.id = a.physiotherapist_id
                WHERE t.status = 'ACTIVE' GROUP BY u.email ORDER BY u.email""", String.class)).containsExactly(
                "fisio.andrade@dev-seed.rehabelite.test:4", "fisio.cevallos@dev-seed.rehabelite.test:2",
                "fisio.paredes@dev-seed.rehabelite.test:1");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM treatments t
                WHERE NOT EXISTS (SELECT 1 FROM responsible_assignments a WHERE a.treatment_id = t.id)""",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT clinical_record_id FROM treatments WHERE status = 'ACTIVE'
                                      GROUP BY clinical_record_id HAVING count(*) > 1) AS twice""", Integer.class))
                .isZero();
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
                String.class)).hasSize(7).allMatch(hash -> hash.startsWith("$2"));

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
        String clinical = clinicalCounts();
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'fisio.cevallos@dev-seed.rehabelite.test'", String.class);

        new ResourceDatabasePopulator(new ClassPathResource("db/dev-seed/R__dev_seed.sql")).execute(dataSource);
        initializer.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients", Integer.class)).isEqualTo(patients);
        assertThat(clinicalCounts()).isEqualTo(clinical).isEqualTo("16|8|8|8|1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients WHERE portal_user_id IS NOT NULL",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'fisio.cevallos@dev-seed.rehabelite.test'",
                String.class)).isEqualTo(hash);
    }

    /** Slots, clinical records, treatments, responsible assignments and status changes (MEJ-04). */
    private String clinicalCounts() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM staff_schedule_slots) || '|' || (SELECT count(*) FROM clinical_records)
                       || '|' || (SELECT count(*) FROM treatments) || '|' || (SELECT count(*) FROM responsible_assignments)
                       || '|' || (SELECT count(*) FROM treatment_status_changes)""", String.class);
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
