package ec.rehabelite.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-18: no role modifies or deletes audit entries. Even with direct SQL, the triggers of V1 reject UPDATE, DELETE
 * and TRUNCATE on audit_logs.
 */
class AuditLogAppendOnlyIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Long entryId;

    @BeforeEach
    void insertEntry() {
        entryId = jdbc.queryForObject("""
                INSERT INTO audit_logs (action, entity_type, entity_id, ip_address)
                VALUES ('READ', 'ClinicalRecord', 77, '10.0.0.5') RETURNING id""", Long.class);
    }

    @Test
    void anUpdateIsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'UPDATE' WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only: UPDATE is not allowed");

        assertUnchanged();
    }

    @Test
    void aDeleteIsRejected() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only: DELETE is not allowed");

        assertUnchanged();
    }

    @Test
    void aTruncateIsRejected() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only: TRUNCATE is not allowed");

        assertUnchanged();
    }

    private void assertUnchanged() {
        Map<String, Object> entry = jdbc.queryForMap(
                "SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE id = ?", entryId);
        assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "ClinicalRecord")
                .containsEntry("entity_id", 77L)
                .containsEntry("ip_address", "10.0.0.5");
    }

}
