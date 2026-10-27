/**
 * Audit.
 * <p>
 * Append-only log of actions on clinical data and denied accesses.
 * Only types in this root package are the module's public API; web, application and domain are internal.
 */
@ApplicationModule(displayName = "Audit")
package ec.rehabelite.audit;

import org.springframework.modulith.ApplicationModule;
