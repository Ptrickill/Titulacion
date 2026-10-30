package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** TEC-06: the test profile (like the default one) never loads the development seed. */
class DevSeedNotLoadedIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    @Test
    void flywayNeverRanTheSeedScript() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE script LIKE '%dev_seed%'",
                Integer.class)).isZero();
    }

    @Test
    void thereAreNoSeedAccountsOrSeedPatients() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE '%" + DevSeedPasswordInitializer.SEED_EMAIL_DOMAIN + "'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients WHERE national_id = '1750000018'",
                Integer.class)).isZero();
    }

    @Test
    void thePasswordInitializerOnlyExistsInTheDevProfile() {
        assertThat(context.getBeansOfType(DevSeedPasswordInitializer.class)).isEmpty();
    }

}
