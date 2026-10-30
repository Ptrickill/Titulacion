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
    void appliesV1ToV3() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2", "3");
        assertThat(flyway.info().applied()).allMatch(migration -> migration.getState().isApplied());
        assertThat(flyway.info().pending()).isEmpty();
    }

    /** HU-18: Envers tables of V3 (the context starting at all proves they match the mapping, ddl-auto=validate). */
    @Test
    void v3CreatesTheEnversTables() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('revinfo', 'treatments_aud', 'therapy_sessions_aud')
                ORDER BY table_name""", String.class))
                .containsExactly("revinfo", "therapy_sessions_aud", "treatments_aud");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'revinfo' AND column_name = 'user_id'""", String.class)).isEqualTo("YES");
    }

    /** Until HU-10 maps TherapySession, its _aud table must mirror therapy_sessions column by column. */
    @Test
    void theTherapySessionVersionsHaveTheSameColumnsAsTheSessions() {
        String columns = """
                SELECT column_name || ':' || data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name NOT IN ('rev', 'revtype') ORDER BY column_name""";
        assertThat(jdbc.queryForList(columns, String.class, "therapy_sessions_aud"))
                .isEqualTo(jdbc.queryForList(columns, String.class, "therapy_sessions"));
        assertThat(jdbc.queryForList(columns, String.class, "treatments_aud"))
                .isEqualTo(jdbc.queryForList(columns, String.class, "treatments"));
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
