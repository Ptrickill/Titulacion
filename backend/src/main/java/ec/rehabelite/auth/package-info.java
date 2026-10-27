/**
 * Authentication.
 * <p>
 * Login with JWT, user management, roles, lockout after failed attempts and security configuration.
 * Only types in this root package are the module's public API; web, application and domain are internal.
 */
@ApplicationModule(displayName = "Authentication")
package ec.rehabelite.auth;

import org.springframework.modulith.ApplicationModule;
