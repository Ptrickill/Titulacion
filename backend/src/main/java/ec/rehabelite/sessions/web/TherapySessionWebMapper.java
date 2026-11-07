package ec.rehabelite.sessions.web;

import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.sessions.application.TherapySessionService.NamedSession;
import ec.rehabelite.sessions.application.TherapySessionService.SessionVersions;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.web.TherapySessionResponses.StaffResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionVersionResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface TherapySessionWebMapper {

    /** The responsible parameter fills the property of the same name; every other one comes from the session. */
    @Mapping(target = "id", source = "session.id")
    TherapySessionResponse toResponse(TherapySession session, StaffResponse responsible);

    default TherapySessionResponse toResponse(NamedSession named) {
        TherapySession session = named.session();
        return toResponse(session, new StaffResponse(session.getResponsibleId(), named.responsibleName()));
    }

    default List<TherapySessionVersionResponse> toResponses(SessionVersions versions) {
        return versions.versions().stream().map(version -> toResponse(version, versions)).toList();
    }

    private TherapySessionVersionResponse toResponse(EntityVersion<TherapySession> version,
            SessionVersions versions) {
        TherapySession session = version.entity();
        StaffResponse changedBy = version.changedBy() == null
                ? null
                : new StaffResponse(version.changedBy(), versions.nameOf(version.changedBy()));
        StaffResponse responsible =
                new StaffResponse(session.getResponsibleId(), versions.nameOf(session.getResponsibleId()));
        return new TherapySessionVersionResponse(version.revision(), version.type(), version.changedAt(), changedBy,
                toResponse(session, responsible));
    }

}
