package ec.rehabelite.shared.domain;

import java.util.Map;

/**
 * The request collides with data that already exists (e.g. a patient with the same national ID). Mapped to
 * HTTP 409. The message is user-facing, so it must be written in Spanish and contain no personal data; the
 * properties (only ids or codes) are added to the problem response so the client can point to the existing data.
 */
public class ConflictException extends RuntimeException {

    private final transient Map<String, Object> properties;

    public ConflictException(String userMessage, Map<String, Object> properties) {
        super(userMessage);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

}
