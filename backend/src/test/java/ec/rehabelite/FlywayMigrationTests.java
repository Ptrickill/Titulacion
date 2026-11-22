package ec.rehabelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.audit.AuditAction;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
    void appliesV1ToV5() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2", "3", "4", "5");
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

    /**
     * Each _aud table mirrors its table column by column, with the same type and length (HU-10 maps TherapySession
     * with @Audited on therapy_sessions_aud as created in V3). A change to either table must change both.
     */
    @Test
    void theVersionTablesHaveTheSameColumnsAsTheirTables() {
        String columns = """
                SELECT column_name || ':' || data_type || ':' || coalesce(character_maximum_length::text, '-')
                FROM information_schema.columns
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

    /**
     * MEJ-01: V5 on a database that already has accounts (a fresh database in the same container, migrated to V4,
     * then to V5). The existing accounts keep their password and count as verified since their creation.
     */
    @Test
    void v5KeepsTheExistingAccountsActivated() {
        String database = "mej01_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("4").load().migrate();
            other.update("""
                    INSERT INTO users (email, password_hash, full_name, role, created_at)
                    VALUES ('existente@rehabelite.test', '$2a$10$hash', 'Cuenta Existente', 'PHYSIOTHERAPIST',
                            '2026-09-01T10:00:00Z')""");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            Map<String, Object> account = other.queryForMap("""
                    SELECT password_hash, email_verified_at = created_at AS verified_since_creation, session_version
                    FROM users WHERE email = 'existente@rehabelite.test'""");
            assertThat(account).containsEntry("password_hash", "$2a$10$hash")
                    .containsEntry("verified_since_creation", true)
                    .containsEntry("session_version", 0);
            assertThat(other.queryForObject("SELECT count(*) FROM account_tokens", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /** MEJ-01: a pending account has no password; a token stores only its 64-character hash, once. */
    @Test
    void v5AllowsAccountsWithoutPasswordAndStoresTokenHashesOnce() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, full_name, role) VALUES (?, 'Pendiente', 'PATIENT') RETURNING id""",
                Long.class, "pendiente-" + UUID.randomUUID() + "@rehabelite.test");
        String hash = "a".repeat(63) + "b";
        jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'ACTIVATION', ?, now() + interval '48 hours')""", userId, hash);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'PASSWORD_RESET', ?, now())""", userId, hash))
                .rootCause().hasMessageContaining("uq_account_tokens_token_hash");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'LOGIN', ?, now())""", userId, "c".repeat(64)))
                .rootCause().hasMessageContaining("ck_account_tokens_purpose");
    }

    /** HU-15: V4 adds EXPORT; the constraint allows exactly the values of AuditAction, and nothing else. */
    @Test
    void v4AllowsTheExportActionAndTheConstraintMatchesTheEnum() {
        jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('EXPORT', 'Report')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('PRINT', 'Report')"))
                .rootCause().hasMessageContaining("ck_audit_logs_action");
        String constraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_audit_logs_action'""",
                String.class);
        for (AuditAction action : AuditAction.values()) {
            assertThat(constraint).contains("'" + action.name() + "'");
        }
        assertThat(constraint.split("'::character varying").length - 1).isEqualTo(AuditAction.values().length);
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
