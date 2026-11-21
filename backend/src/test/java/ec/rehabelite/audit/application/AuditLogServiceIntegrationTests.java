package ec.rehabelite.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordTakesTheUserFromTheSecurityContextAndTheIpFromTheRequest() {
        Long userId = insertUser();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(String.valueOf(userId), null, "ROLE_PHYSIOTHERAPIST"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.10.25");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditLogService.record(AuditAction.READ, "ClinicalRecord", 99L);

        Map<String, Object> entry = jdbc.queryForMap(
                "SELECT action, entity_type, entity_id, ip_address, occurred_at FROM audit_logs WHERE user_id = ?",
                userId);
        assertThat(entry).containsEntry("action", "READ")
                .containsEntry("entity_type", "ClinicalRecord")
                .containsEntry("entity_id", 99L)
                .containsEntry("ip_address", "192.168.10.25");
        assertThat(entry.get("occurred_at")).isNotNull();
    }

    @Test
    void recordForStoresTheGivenUserWithoutARequest() {
        Long userId = insertUser();
        // The Spring test framework binds a mock request to the test thread; remove it to simulate a background job
        RequestContextHolder.resetRequestAttributes();

        auditLogService.recordFor(userId, AuditAction.LOGIN, "User", userId);

        Map<String, Object> entry = jdbc.queryForMap(
                "SELECT action, entity_id, ip_address FROM audit_logs WHERE user_id = ?", userId);
        assertThat(entry).containsEntry("action", "LOGIN")
                .containsEntry("entity_id", userId)
                .containsEntry("ip_address", null);
    }

    @Test
    void recordAllStoresOneEntryPerEntityWithASingleInsert() {
        Long userId = insertUser();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(String.valueOf(userId), null, "ROLE_PATIENT"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.10.25");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        auditLogService.recordAll(AuditAction.READ, "Treatment", List.of(11L, 12L, 13L));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        List<Map<String, Object>> entries = jdbc.queryForList("""
                SELECT action, entity_type, entity_id, ip_address, occurred_at FROM audit_logs
                WHERE user_id = ? ORDER BY id""", userId);
        assertThat(entries).extracting(entry -> entry.get("entity_id")).containsExactly(11L, 12L, 13L);
        assertThat(entries).allSatisfy(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Treatment")
                .containsEntry("ip_address", "192.168.10.25")
                .containsEntry("occurred_at", entries.getFirst().get("occurred_at")));
        assertThat(((Timestamp) entries.getFirst().get("occurred_at")).toInstant())
                .isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
    }

    @Test
    void recordAllWorksWithoutAUserNorARequestAndRecordsNothingForAnEmptyCollection() {
        RequestContextHolder.resetRequestAttributes();
        long entityId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

        auditLogService.recordAll(AuditAction.READ, "RecordAllTest", List.of());
        auditLogService.recordAll(AuditAction.READ, "RecordAllTest", List.of(entityId));

        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT user_id, entity_id, ip_address FROM audit_logs WHERE entity_type = 'RecordAllTest'"
                        + " AND entity_id = ?", entityId);
        assertThat(entries).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("user_id", null)
                .containsEntry("ip_address", null));
    }

    private Long insertUser() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Usuario de auditoría', 'PHYSIOTHERAPIST')
                RETURNING id""", Long.class, "auditoria-" + UUID.randomUUID() + "@rehabelite.test");
    }

}
