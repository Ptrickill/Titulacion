package ec.rehabelite.auth.config;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers 403 with a Spanish problem response and records ACCESS_DENIED in the audit log, attributed to
 * the authenticated user. The entity is taken from the path ({@code /api/patients/12} → {@code patients}, 12);
 * the query string is never stored because it may contain personal data.
 */
@Component
@RequiredArgsConstructor
class AuditingAccessDeniedHandler implements AccessDeniedHandler {

    static final String TITLE = "Acceso denegado";
    static final String DETAIL = "No tiene permisos para realizar esta acción.";
    static final String UNKNOWN_ENTITY = "unknown";

    private static final String API_PREFIX = "/api/";
    private static final int ENTITY_TYPE_MAX_LENGTH = 60;
    private static final Logger log = LoggerFactory.getLogger(AuditingAccessDeniedHandler.class);

    private final AuditLogService auditLogService;
    private final ProblemResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        AuditedResource resource = AuditedResource.of(pathWithinApplication(request));
        try {
            auditLogService.record(AuditAction.ACCESS_DENIED, resource.entityType(), resource.entityId());
        } catch (RuntimeException auditFailure) {
            // The 403 must still be sent; the failure is logged without request data
            log.error("Could not record ACCESS_DENIED entityType={} type={}", resource.entityType(),
                    auditFailure.getClass().getName());
        }
        log.info("Access denied method={} entityType={} entityId={}", request.getMethod(), resource.entityType(),
                resource.entityId());
        writer.write(request, response, HttpStatus.FORBIDDEN, TITLE, DETAIL);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    /** Entity recorded for a denied request: the API resource and, if the next segment is numeric, its id. */
    record AuditedResource(String entityType, Long entityId) {

        static AuditedResource of(String path) {
            if (path == null || !path.startsWith(API_PREFIX)) {
                return new AuditedResource(UNKNOWN_ENTITY, null);
            }
            String[] segments = path.substring(API_PREFIX.length()).split("/");
            if (segments[0].isBlank()) {
                return new AuditedResource(UNKNOWN_ENTITY, null);
            }
            String entityType = segments[0].length() > ENTITY_TYPE_MAX_LENGTH
                    ? segments[0].substring(0, ENTITY_TYPE_MAX_LENGTH)
                    : segments[0];
            return new AuditedResource(entityType, segments.length > 1 ? parseId(segments[1]) : null);
        }

        private static Long parseId(String segment) {
            if (!segment.matches("\\d{1,18}")) {
                return null;
            }
            return Long.valueOf(segment);
        }

    }

}
