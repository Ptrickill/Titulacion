package ec.rehabelite.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import java.util.Map;
import java.util.UUID;
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

    private Long insertUser() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Usuario de auditoría', 'PHYSIOTHERAPIST')
                RETURNING id""", Long.class, "auditoria-" + UUID.randomUUID() + "@rehabelite.test");
    }

}
