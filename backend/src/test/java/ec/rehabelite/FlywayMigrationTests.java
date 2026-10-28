package ec.rehabelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the Flyway migrations against PostgreSQL 16 (Testcontainers).
 */
class FlywayMigrationTests extends AbstractIntegrationTest {

    private final Flyway flyway;
    private final JdbcTemplate jdbc;

    @Autowired
    FlywayMigrationTests(Flyway flyway, JdbcTemplate jdbc) {
        this.flyway = flyway;
        this.jdbc = jdbc;
    }

    @Test
    void runsOnPostgres16() {
        String version = jdbc.queryForObject("SHOW server_version", String.class);
        assertThat(version).startsWith("16.");
    }

    @Test
    void appliesV1AndV2() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2");
        assertThat(flyway.info().applied()).allMatch(migration -> migration.getState().isApplied());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void icd10CatalogHasRows() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM icd10_diagnoses", Integer.class);
        assertThat(total).isGreaterThan(0);
    }

    @Test
    void normalizeTextIgnoresAccentsAndCase() {
        String normalized = jdbc.queryForObject("SELECT normalize_text('ÁLVAREZ Peña')", String.class);
        assertThat(normalized).isEqualTo("alvarez pena");
    }

    @Test
    void auditLogsAreAppendOnly() {
        jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('LOGIN', 'users')");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET entity_type = 'other'"))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs"))
                .rootCause().hasMessageContaining("append-only");
    }

}
