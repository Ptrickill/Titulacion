package ec.rehabelite.audit.domain;

import org.hibernate.envers.RevisionListener;

/** Fills the author of each Envers revision from the authenticated user, if there is one. */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).assignUser(AuthenticatedUser.currentId());
    }

}
