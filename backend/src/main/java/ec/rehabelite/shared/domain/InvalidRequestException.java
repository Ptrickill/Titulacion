package ec.rehabelite.shared.domain;

/**
 * The request is invalid for a reason Bean Validation cannot express (e.g. a date range whose start is after its
 * end). Mapped to HTTP 400 "Solicitud no válida" with this message as the detail. The message is user-facing, so it
 * must be written in Spanish and contain no personal data.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String userMessage) {
        super(userMessage);
    }

}
