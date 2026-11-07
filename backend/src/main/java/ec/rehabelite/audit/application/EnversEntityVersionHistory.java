package ec.rehabelite.audit.application;

import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.audit.EntityVersionHistory;
import ec.rehabelite.audit.domain.AuditRevision;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link EntityVersionHistory} with Envers' AuditReader: one query over the entity's _aud table. */
@Service
@RequiredArgsConstructor
class EnversEntityVersionHistory implements EntityVersionHistory {

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public <T> List<EntityVersion<T>> versionsOf(Class<T> entityType, Long id) {
        List<?> rows = AuditReaderFactory.get(entityManager).createQuery()
                // false: also the revision entity and type for each version; true: include deletions
                .forRevisionsOfEntity(entityType, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(row -> versionOf(entityType, (Object[]) row)).toList();
    }

    private static <T> EntityVersion<T> versionOf(Class<T> entityType, Object[] row) {
        AuditRevision revision = (AuditRevision) row[1];
        return new EntityVersion<>(entityType.cast(row[0]), revision.getId(), typeOf((RevisionType) row[2]),
                revision.getTimestamp(), revision.getUserId());
    }

    private static EntityVersion.Type typeOf(RevisionType type) {
        return switch (type) {
            case ADD -> EntityVersion.Type.CREATE;
            case MOD -> EntityVersion.Type.UPDATE;
            case DEL -> EntityVersion.Type.DELETE;
        };
    }

}
