package ec.rehabelite.shared.domain;

/**
 * A requested resource does not exist. Mapped to HTTP 404.
 * The message is user-facing, so it must be written in Spanish and contain no personal data.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String userMessage) {
        super(userMessage);
    }

}
