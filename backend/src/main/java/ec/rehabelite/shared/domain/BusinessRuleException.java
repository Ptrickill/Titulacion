package ec.rehabelite.shared.domain;

/**
 * A business rule was violated (e.g. registering a session on a completed treatment). Mapped to HTTP 422.
 * The message is user-facing, so it must be written in Spanish and contain no personal data.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String userMessage) {
        super(userMessage);
    }

}
