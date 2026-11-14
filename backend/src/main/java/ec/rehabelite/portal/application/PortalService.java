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
import ec.rehabelite.sessions.TherapySessionQueryPort;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only patient portal (HU-12). The patient is always the one linked to the authenticated account
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
        PatientCard patient = linkedPatient();
        TreatmentSummary treatment = treatmentQuery.find(treatmentId)
                .filter(found -> found.patientId().equals(patient.id()))
                .orElseThrow(() -> {
                    log.info("Portal treatment denied treatmentId={} patientId={}", treatmentId, patient.id());
                    return new ForbiddenOperationException(TREATMENT_NOT_ACCESSIBLE);
                });
        TreatmentProgress progress = TreatmentProgress.of(treatment, sessionQuery.chronological(treatmentId));

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return progress;
    }

    private PatientCard linkedPatient() {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("The patient portal needs an authenticated user"));
        return patientDirectory.findByPortalUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NO_LINKED_PATIENT));
    }

}
