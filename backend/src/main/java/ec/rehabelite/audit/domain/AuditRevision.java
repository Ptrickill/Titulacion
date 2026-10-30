package ec.rehabelite.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Hibernate Envers revision (HU-18): one per transaction that changes an audited entity (treatments, and therapy
 * sessions from HU-10), with when it happened and who made it. The versions themselves live in the *_aud tables.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
@Getter
@NoArgsConstructor
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_timestamp", nullable = false)
    private Instant timestamp;

    /** Author of the change; null without an authenticated user (initializers, dev seed). */
    @Column(name = "user_id")
    private Long userId;

    void assignUser(Long userId) {
        this.userId = userId;
    }

}
