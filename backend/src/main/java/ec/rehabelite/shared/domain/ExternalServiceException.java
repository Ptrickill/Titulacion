package ec.rehabelite.shared.domain;

/**
 * An external service the operation depends on failed (MEJ-01: the SMTP server that sends the activation email),
 * so the operation was not completed. Mapped to HTTP 503 "Servicio no disponible" with this message as the detail.
 * The message is user-facing, so it must be written in Spanish and contain no personal data.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }

}
