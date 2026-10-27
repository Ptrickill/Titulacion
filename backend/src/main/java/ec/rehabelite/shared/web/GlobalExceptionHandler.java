package ec.rehabelite.shared.web;

import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception raised by a controller into an RFC 9457 {@link ProblemDetail}
 * whose title and detail are in Spanish. Logs never include personal or clinical data.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private record Message(String title, String detail) {
    }

    private static final Message DEFAULT_MESSAGE = new Message("Error", "No se pudo procesar la solicitud.");

    private static final Map<Integer, Message> MESSAGES_BY_STATUS = Map.ofEntries(
            Map.entry(400, new Message("Solicitud no válida", "La solicitud no tiene un formato válido.")),
            Map.entry(401, new Message("No autenticado", "Debe iniciar sesión para acceder a este recurso.")),
            Map.entry(403, new Message("Acceso denegado", "No tiene permisos para realizar esta acción.")),
            Map.entry(404, new Message("Recurso no encontrado", "El recurso solicitado no existe.")),
            Map.entry(405, new Message("Método no permitido", "El método HTTP no está permitido para este recurso.")),
            Map.entry(406, new Message("Formato no aceptable", "No se puede generar la respuesta en el formato solicitado.")),
            Map.entry(409, new Message("Conflicto", "La operación entra en conflicto con el estado actual de los datos.")),
            Map.entry(413, new Message("Solicitud demasiado grande", "El contenido enviado supera el tamaño permitido.")),
            Map.entry(415, new Message("Tipo de contenido no soportado", "El tipo de contenido de la solicitud no está soportado.")),
            Map.entry(422, new Message("Regla de negocio incumplida", "La operación no cumple las reglas del negocio.")),
            Map.entry(500, new Message("Error interno", "Ocurrió un error inesperado. Intente nuevamente más tarde.")),
            Map.entry(503, new Message("Servicio no disponible", "El servicio no está disponible en este momento.")));

    static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(messageFor(status).title());
        return problem;
    }

    private static Message messageFor(HttpStatusCode status) {
        return MESSAGES_BY_STATUS.getOrDefault(status.value(), DEFAULT_MESSAGE);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // The driver message contains the offending values (personal data), so only the constraint name is logged
        log.warn("Data integrity violation: constraint={}", constraintName(ex));
        return problem(HttpStatus.CONFLICT, "La operación viola una restricción de integridad de los datos.");
    }

    // Method-security denials reach this handler instead of the security filter chain.
    // TODO(HU-01): record ACCESS_DENIED in the audit log here as well.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, messageFor(HttpStatus.FORBIDDEN).detail());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception errorId={} type={}", errorId, ex.getClass().getName());
        // Exception messages may carry personal data: the stack trace is only logged at DEBUG level
        log.debug("Stack trace for errorId={}", errorId, ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, messageFor(HttpStatus.INTERNAL_SERVER_ERROR).detail());
        problem.setProperty("errorId", errorId);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorResponse(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(status, "La solicitud contiene datos no válidos.");
        problem.setTitle("Datos no válidos");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = ex.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage).toList();
        ProblemDetail problem = problem(status, "La solicitud contiene datos no válidos.");
        problem.setTitle("Datos no válidos");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    /** Replaces the English title/detail Spring MVC uses for its built-in exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Message message = messageFor(statusCode);
            problem.setTitle(message.title());
            problem.setDetail(message.detail());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
        }
        return "unknown";
    }

    record FieldErrorResponse(String field, String message) {
    }

}
