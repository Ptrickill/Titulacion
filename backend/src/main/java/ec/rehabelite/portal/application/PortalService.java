package ec.rehabelite.portal.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.portal.domain.TreatmentProgress;
import ec.rehabelite.sessions.PatientSessionView;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only patient portal (HU-12, HU-13). The patient is always the one linked to the authenticated account
 * (patients.portal_user_id): no method takes a patient id, so a patient cannot ask for the data of another one.
 * Patients, treatments, sessions and staff are read only through the public APIs of their modules.
 * <p>
 * Every read is audited (HU-18), so the methods are transactional without readOnly.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    static final String TREATMENT_ENTITY = "Treatment";
    static final String NO_LINKED_PATIENT = "La cuenta no tiene un paciente vinculado.";
    static final String TREATMENT_NOT_ACCESSIBLE = "No tiene acceso a ese tratamiento.";

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

    private final CurrentUserProvider currentUserProvider;
    private final PatientDirectory patientDirectory;
    private final TreatmentQueryPort treatmentQuery;
    private final TherapySessionQueryPort sessionQuery;
    private final StaffDirectory staffDirectory;
    private final AuditLogService auditLogService;

    /**
     * A treatment of the patient with the name of its current responsible (null if it has none or the account no
     * longer exists) and the sessions that took place.
     */
    public record PortalTreatment(PatientTreatment treatment, String responsibleName, long completedSessions) {
    }

    /** A session as the patient reads it, with the name of the physiotherapist (null if the account is gone). */
    public record PortalSession(PatientSessionView session, String responsibleName) {
    }

    /**
     * The last instructions a physiotherapist wrote for the patient.
     *
     * @param sessionDate     day of the session they were written in
     * @param sequenceNumber  sequence number of that session
     * @param responsibleName physiotherapist who wrote them; null if the account no longer exists
     */
    public record PortalInstruction(String instructions, LocalDate sessionDate, int sequenceNumber,
            String responsibleName) {

        /** Never print clinical data, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "PortalInstruction[sequenceNumber=" + sequenceNumber + "]";
        }

    }

    /**
     * The treatments of the authenticated patient: the active one first, then the others from the most recent to
     * the oldest; empty when the patient has none. Each treatment listed is audited as READ, all in one INSERT. A
     * fixed number of statements, whatever the number of treatments: the patient, the treatments, their current
     * responsibles, the names, the completed counts and the audit entries.
     *
     * @throws ResourceNotFoundException if the account has no linked patient (not audited)
     */
    @Transactional
    public List<PortalTreatment> treatments() {
        PatientCard patient = linkedPatient();
        List<PatientTreatment> treatments = treatmentQuery.findByPatient(patient.id());
        if (treatments.isEmpty()) {
            return List.of();
        }
        List<Long> treatmentIds = treatments.stream().map(PatientTreatment::id).toList();
        Set<Long> responsibleIds = treatments.stream()
                .map(PatientTreatment::responsibleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = responsibleIds.isEmpty() ? Map.of() : staffDirectory.fullNames(responsibleIds);
        Map<Long, Long> completed = sessionQuery.completedCounts(treatmentIds);

        auditLogService.recordAll(AuditAction.READ, TREATMENT_ENTITY, treatmentIds);
        return treatments.stream()
                .map(treatment -> new PortalTreatment(treatment,
                        treatment.responsibleId() == null ? null : names.get(treatment.responsibleId()),
                        completed.getOrDefault(treatment.id(), 0L)))
                .toList();
    }

    /**
     * The progress of one treatment of the authenticated patient; the read is audited as READ of the treatment. A
     * fixed number of statements, whatever the number of sessions: the patient, the treatment, its sessions and the
     * audit entry.
     * <p>
     * A treatment of another patient and a treatment that does not exist get the same answer, so the API never
     * tells a patient which treatment ids exist.
     *
     * @throws ResourceNotFoundException if the account has no linked patient (not audited)
     * @throws ForbiddenOperationException if the treatment is not one of the patient's (403, audited as
     *         ACCESS_DENIED)
     */
    @Transactional
    public TreatmentProgress progress(Long treatmentId) {
        TreatmentSummary treatment = ownTreatment(treatmentId);
        TreatmentProgress progress = TreatmentProgress.of(treatment, sessionQuery.chronological(treatmentId));

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return progress;
    }

    /**
     * The sessions of one treatment of the authenticated patient (HU-13), in chronological order and whatever their
     * status, each with the name of its physiotherapist. Never the clinical observations nor the progress status:
     * the sessions module does not hand them over. The read is audited as READ of the treatment. A fixed number of
     * statements, whatever the number of sessions: the patient, the treatment, its sessions, the names in one call
     * and the audit entry.
     *
     * @throws ResourceNotFoundException if the account has no linked patient (not audited)
     * @throws ForbiddenOperationException if the treatment is not one of the patient's (403, audited as
     *         ACCESS_DENIED)
     */
    @Transactional
    public List<PortalSession> sessions(Long treatmentId) {
        ownTreatment(treatmentId);
        List<PatientSessionView> sessions = sessionQuery.patientView(treatmentId);
        Set<Long> responsibleIds = sessions.stream()
                .map(PatientSessionView::responsibleId)
                .collect(Collectors.toSet());
        Map<Long, String> names = responsibleIds.isEmpty() ? Map.of() : staffDirectory.fullNames(responsibleIds);

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return sessions.stream()
                .map(session -> new PortalSession(session, names.get(session.responsibleId())))
                .toList();
    }

    /**
     * The last instructions written for the patient in one treatment of the authenticated patient (HU-13): those of
     * the most recent session that has any, whatever its status. Empty when no session has instructions; that read
     * is audited too, as READ of the treatment. A fixed number of statements: the patient, the treatment, one
     * session, the name of its physiotherapist and the audit entry.
     *
     * @throws ResourceNotFoundException if the account has no linked patient (not audited)
     * @throws ForbiddenOperationException if the treatment is not one of the patient's (403, audited as
     *         ACCESS_DENIED)
     */
    @Transactional
    public Optional<PortalInstruction> latestInstruction(Long treatmentId) {
        ownTreatment(treatmentId);
        Optional<PortalInstruction> instruction = sessionQuery.latestPatientInstruction(treatmentId)
                .map(session -> new PortalInstruction(session.patientInstructions(), session.sessionDate(),
                        session.sequenceNumber(),
                        staffDirectory.fullNames(Set.of(session.responsibleId())).get(session.responsibleId())));

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return instruction;
    }

    /**
     * The treatment, only if it belongs to the authenticated patient. A treatment of another patient and one that
     * does not exist get the same answer, so the API never tells a patient which treatment ids exist.
     */
    private TreatmentSummary ownTreatment(Long treatmentId) {
        PatientCard patient = linkedPatient();
        return treatmentQuery.find(treatmentId)
                .filter(found -> found.patientId().equals(patient.id()))
                .orElseThrow(() -> {
                    log.info("Portal treatment denied treatmentId={} patientId={}", treatmentId, patient.id());
                    return new ForbiddenOperationException(TREATMENT_NOT_ACCESSIBLE);
                });
    }

    private PatientCard linkedPatient() {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("The patient portal needs an authenticated user"));
        return patientDirectory.findByPortalUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NO_LINKED_PATIENT));
    }

}
