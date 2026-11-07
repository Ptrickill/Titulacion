package ec.rehabelite.shared.domain;

import org.springframework.security.access.AccessDeniedException;

/**
 * The user's role may use the endpoint, but not on this resource (e.g. correcting a therapy session registered by
 * another physiotherapist). Mapped to HTTP 403 by the security filter chain, which also records ACCESS_DENIED in the
 * audit log; unlike other access denials, the response detail is this message. It is user-facing, so it must be
 * written in Spanish and contain no personal data.
 */
public class ForbiddenOperationException extends AccessDeniedException {

    public ForbiddenOperationException(String userMessage) {
        super(userMessage);
    }

}
