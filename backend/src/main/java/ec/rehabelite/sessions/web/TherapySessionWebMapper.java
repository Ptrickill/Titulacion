package ec.rehabelite.sessions.web;

import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.sessions.application.TherapySessionService.NamedSession;
import ec.rehabelite.sessions.application.TherapySessionService.SessionVersions;
import ec.rehabelite.sessions.application.TherapySessionService.Timeline;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.web.TherapySessionResponses.DiagnosisResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.PainPointResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.StaffResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionVersionResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TimelineResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TimelineTreatmentResponse;
import java.time.LocalDate;
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

    default TimelineResponse toResponse(Timeline timeline, LocalDate from, LocalDate to) {
        TreatmentSummary treatment = timeline.treatment();
        TimelineTreatmentResponse treatmentResponse = new TimelineTreatmentResponse(treatment.id(),
                treatment.patientId(), treatment.active(), treatment.startDate(), treatment.initialPainScore(),
                treatment.plannedSessions(), timeline.completedSessions(),
                new DiagnosisResponse(treatment.diagnosisCode(), treatment.diagnosisDescription()));
        List<TherapySessionResponse> sessions = timeline.sessions().stream()
                .map(session -> toResponse(session,
                        new StaffResponse(session.getResponsibleId(), timeline.nameOf(session.getResponsibleId()))))
                .toList();
        List<PainPointResponse> painCurve = timeline.painCurve().stream()
                .map(point -> new PainPointResponse(point.label(), point.date(), point.painScore(), point.sessionId(),
                        point.sequenceNumber()))
                .toList();
        return new TimelineResponse(treatmentResponse, from, to, sessions, painCurve);
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
