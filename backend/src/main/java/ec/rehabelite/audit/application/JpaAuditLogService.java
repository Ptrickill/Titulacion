package ec.rehabelite.audit.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.audit.domain.AuditLog;
import ec.rehabelite.audit.domain.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Stores audit entries in {@code audit_logs}.
 * <p>
 * The acting user is read from Spring Security's context (the JWT subject is the user id), so this module
 * does not depend on the auth module, which itself records logins here.
 */
@Service
@RequiredArgsConstructor
class JpaAuditLogService implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void record(AuditAction action, String entityType, Long entityId) {
        recordFor(currentUserId(), action, entityType, entityId);
    }

    @Override
    @Transactional
    public void recordFor(Long userId, AuditAction action, String entityType, Long entityId) {
        AuditLog entry = auditLogRepository.save(
                new AuditLog(userId, action, entityType, entityId, clock.instant(), clientIp()));
        log.debug("Audit entry recorded id={} action={} entityType={}", entry.getId(), action, entityType);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Client IP of the current request; behind a proxy it relies on server.forward-headers-strategy. */
    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getRemoteAddr();
        if (ip == null || ip.length() <= AuditLog.IP_ADDRESS_MAX_LENGTH) {
            return ip;
        }
        return ip.substring(0, AuditLog.IP_ADDRESS_MAX_LENGTH);
    }

}
