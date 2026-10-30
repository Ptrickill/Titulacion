package ec.rehabelite.audit.domain;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user of the current request, read from Spring Security's context (the JWT subject is the user id), so this
 * module does not depend on the auth module.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    /** The authenticated user's id, or null when the code runs without one (anonymous, startup, scheduled). */
    public static Long currentId() {
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

}
