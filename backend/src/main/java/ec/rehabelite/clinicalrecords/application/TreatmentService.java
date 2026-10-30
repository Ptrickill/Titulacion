package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChange;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChangeRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traceability of a treatment (HU-08): changes its responsible physiotherapist and its status, keeping both
 * histories. Staff accounts are looked up only through {@link StaffDirectory}.
 */
@Service
@RequiredArgsConstructor
public class TreatmentService {

    static final String TREATMENT_NOT_FOUND = "El tratamiento no existe.";
    static final String RESPONSIBLE_NOT_ACTIVE_PHYSIOTHERAPIST =
            "El nuevo responsable debe ser un fisioterapeuta activo.";

    private static final Logger log = LoggerFactory.getLogger(TreatmentService.class);

    private final TreatmentRepository treatmentRepository;
    private final ResponsibleAssignmentRepository assignmentRepository;
    private final TreatmentStatusChangeRepository statusChangeRepository;
    private final StaffDirectory staffDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** A treatment with its responsible and status histories (most recent first) and the names of the users. */
    public record TreatmentHistory(Treatment treatment, List<ResponsibleAssignment> responsibles,
            List<TreatmentStatusChange> statusChanges, Map<Long, String> userNames) {

        public String nameOf(Long userId) {
            return userNames.get(userId);
        }

    }

    /**
     * The treatment with its plan; the read is audited as READ (HU-18). Not read-only: the audit entry is inserted
     * in the same transaction.
     *
     * @throws ResourceNotFoundException if the treatment does not exist (not audited)
     */
    @Transactional
    public Treatment find(Long treatmentId) {
        Treatment treatment = treatmentRepository.findWithDetailsById(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException(TREATMENT_NOT_FOUND));
        auditLogService.record(AuditAction.READ, ClinicalRecordService.TREATMENT_ENTITY, treatment.getId());
        return treatment;
    }

    /**
     * The responsible and status histories; the read is audited as READ (HU-18).
     *
     * @throws ResourceNotFoundException if the treatment does not exist (not audited)
     */
    @Transactional
    public TreatmentHistory history(Long treatmentId) {
        Treatment treatment = treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException(TREATMENT_NOT_FOUND));
        auditLogService.record(AuditAction.READ, ClinicalRecordService.TREATMENT_ENTITY, treatment.getId());
        return historyOf(treatment);
    }

    /** Active physiotherapists who can become responsible of a treatment. */
    @Transactional(readOnly = true)
    public List<StaffMember> physiotherapists() {
        return staffDirectory.activePhysiotherapists();
    }

    /**
     * Makes another physiotherapist the current responsible: the current assignment ends now and a new one starts.
     * Choosing the current responsible changes and audits nothing; a real change is audited as UPDATE.
     *
     * @throws ResourceNotFoundException if the treatment does not exist
     * @throws BusinessRuleException if the user is not an active physiotherapist or the treatment is not active
     */
    @Transactional
    public TreatmentHistory changeResponsible(Long treatmentId, Long physiotherapistId) {
        Treatment treatment = lockedTreatment(treatmentId);
        if (staffDirectory.findActivePhysiotherapist(physiotherapistId).isEmpty()) {
            throw new BusinessRuleException(RESPONSIBLE_NOT_ACTIVE_PHYSIOTHERAPIST);
        }
        Long userId = currentUserId();
        Optional<ResponsibleAssignment> next = treatment.reassignResponsible(physiotherapistId, userId,
                clock.instant());
        if (next.isPresent()) {
            // The ended assignment must reach the database before the new one: a partial unique index allows a
            // single current assignment, and Hibernate would otherwise insert first
            treatmentRepository.flush();
            assignmentRepository.save(next.get());
            auditLogService.record(AuditAction.UPDATE, ClinicalRecordService.TREATMENT_ENTITY, treatmentId);
            log.info("Treatment responsible changed treatmentId={} assignmentId={} changedBy={}", treatmentId,
                    next.get().getId(), userId);
        }
        return historyOf(treatment);
    }

    /**
     * Moves the treatment to a new status, keeps the change (previous and new status, reason, user and date) and
     * audits it as STATUS_CHANGE.
     *
     * @throws ResourceNotFoundException if the treatment does not exist
     * @throws BusinessRuleException if the transition is not allowed, the reason is blank or, when resuming,
     *         another treatment of the record is active
     */
    @Transactional
    public TreatmentHistory changeStatus(Long treatmentId, TreatmentStatus newStatus, String reason) {
        Treatment treatment = lockedTreatment(treatmentId);
        Long userId = currentUserId();
        TreatmentStatusChange change = treatment.changeStatus(newStatus, reason, userId, clock.instant());
        statusChangeRepository.save(change);

        auditLogService.record(AuditAction.STATUS_CHANGE, ClinicalRecordService.TREATMENT_ENTITY, treatmentId);
        log.info("Treatment status changed treatmentId={} statusChangeId={} changedBy={}", treatmentId,
                change.getId(), userId);
        return historyOf(treatment);
    }

    private Treatment lockedTreatment(Long treatmentId) {
        return treatmentRepository.findForUpdate(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException(TREATMENT_NOT_FOUND));
    }

    private TreatmentHistory historyOf(Treatment treatment) {
        List<ResponsibleAssignment> responsibles =
                assignmentRepository.findByTreatmentIdOrderByValidFromDescIdDesc(treatment.getId());
        List<TreatmentStatusChange> statusChanges =
                statusChangeRepository.findByTreatmentIdOrderByChangedAtDescIdDesc(treatment.getId());
        Set<Long> userIds = new HashSet<>();
        responsibles.forEach(assignment -> userIds.add(assignment.getPhysiotherapistId()));
        statusChanges.forEach(change -> userIds.add(change.getUserId()));
        return new TreatmentHistory(treatment, responsibles, statusChanges, staffDirectory.fullNames(userIds));
    }

    private Long currentUserId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("A treatment change needs an authenticated user"));
    }

}
