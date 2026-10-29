package ec.rehabelite.auth;

import java.util.Optional;

/**
 * Public API of the auth module: lets other modules know who is making the current request.
 */
public interface CurrentUserProvider {

    /** The authenticated user, or empty for anonymous requests. */
    Optional<CurrentUser> currentUser();

    /** Id of the authenticated user, or empty for anonymous requests. */
    default Optional<Long> currentUserId() {
        return currentUser().map(CurrentUser::id);
    }

}
