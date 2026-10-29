package ec.rehabelite.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.config.AuditingAccessDeniedHandler.AuditedResource;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class AuditingAccessDeniedHandlerTests {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuditingAccessDeniedHandler handler = new AuditingAccessDeniedHandler(auditLogService,
            new ProblemResponseWriter(JsonMapper.builder().build()));

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "/api/patients,                      patients,         null",
        "/api/patients/12,                   patients,         12",
        "/api/treatments/7/status-changes,   treatments,       7",
        "/api/clinical-records/abc,          clinical-records, null",
        "/api/reports/,                      reports,          null",
        "/api/,                              unknown,          null",
        "/actuator/env,                      unknown,          null",
        "/api/portal/99999999999999999999,   portal,           null"
    })
    void derivesTheAuditedEntityFromThePath(String path, String entityType, Long entityId) {
        assertThat(AuditedResource.of(path)).isEqualTo(new AuditedResource(entityType, entityId));
    }

    @Test
    void answers403InSpanishAndRecordsAccessDeniedWithoutTheQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/patients/12");
        request.setQueryString("query=1712345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(auditLogService).record(AuditAction.ACCESS_DENIED, "patients", 12L);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"title\":\"Acceso denegado\"")
                .contains("\"detail\":\"No tiene permisos para realizar esta acción.\"")
                .contains("\"instance\":\"/api/patients/12\"")
                .doesNotContain("1712345678");
    }

    @Test
    void stillAnswers403WhenTheAuditLogFails() throws Exception {
        doThrow(new IllegalStateException("database down")).when(auditLogService)
                .record(any(AuditAction.class), anyString(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/reports"), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

}
