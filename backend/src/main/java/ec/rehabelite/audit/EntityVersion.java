package ec.rehabelite.audit;

import java.time.Instant;

/**
 * One version of an entity versioned with Hibernate Envers (HU-18): the state of the entity after a revision, with
 * when and by whom it was made.
 *
 * @param entity    the entity as it was saved in that revision (detached; never modify it)
 * @param revision  revision number, increasing over time
 * @param type      whether the revision created or changed the entity
 * @param changedAt time of the revision
 * @param changedBy author of the revision, or {@code null} without an authenticated user (initializers, dev seed)
 */
public record EntityVersion<T>(T entity, long revision, Type type, Instant changedAt, Long changedBy) {

    public enum Type {
        CREATE, UPDATE, DELETE
    }

}
