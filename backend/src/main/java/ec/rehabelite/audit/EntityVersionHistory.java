package ec.rehabelite.audit;

import java.util.List;

/**
 * Public API of the audit module: reads the versions Hibernate Envers keeps of an {@code @Audited} entity, so other
 * modules never touch the revision entity (revinfo) or Envers itself.
 */
public interface EntityVersionHistory {

    /** Every version of the entity, oldest first; empty when it has none. */
    <T> List<EntityVersion<T>> versionsOf(Class<T> entityType, Long id);

}
