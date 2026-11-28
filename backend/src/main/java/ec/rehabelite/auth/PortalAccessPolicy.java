package ec.rehabelite.auth;

import java.util.Optional;

/**
 * Whether the patient of a portal account may use the portal (MEJ-06): it needs a valid DATA_PROCESSING consent and
 * must not be blocked by an erasure request. Declared here and implemented by the patients module, so reactivating a
 * portal account can check it without auth depending on patients.
 */
public interface PortalAccessPolicy {

    /**
     * Why the portal account may not be active, in Spanish, or empty when it may (also for an account without a
     * patient, or a staff account).
     */
    Optional<String> portalAccessDenial(Long userId);

}
