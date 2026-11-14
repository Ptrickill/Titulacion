package ec.rehabelite.clinicalrecords.application;

import static java.util.stream.Collectors.toMap;

import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link TreatmentQueryPort} for the other modules. */
@Service
@RequiredArgsConstructor
class TreatmentQueryService implements TreatmentQueryPort {

    /** The active treatment first (a patient has at most one), then from the most recent to the oldest. */
    private static final Comparator<Treatment> ACTIVE_THEN_MOST_RECENT = Comparator
            .comparing((Treatment treatment) -> !treatment.isActive())
            .thenComparing(Treatment::getStartDate, Comparator.reverseOrder())
            .thenComparing(Treatment::getId, Comparator.reverseOrder());

    private final TreatmentRepository treatmentRepository;
    private final ResponsibleAssignmentRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TreatmentSummary> find(Long treatmentId) {
        return treatmentRepository.findSummaryById(treatmentId).map(TreatmentQueryService::summaryOf);
    }

    /** MANDATORY: the lock is only useful while the caller's transaction is still open. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TreatmentSummary> lockForSessionRegistration(Long treatmentId) {
        return treatmentRepository.findForUpdate(treatmentId).map(TreatmentQueryService::summaryOf);
    }

    /** The treatments with their diagnosis, and the current responsible of all of them in one IN query. */
    @Override
    @Transactional(readOnly = true)
    public List<PatientTreatment> findByPatient(Long patientId) {
        List<Treatment> treatments = treatmentRepository.findByPatientId(patientId);
        if (treatments.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> responsibleIds = assignmentRepository
                .findCurrentByTreatmentIds(treatments.stream().map(Treatment::getId).toList())
                .stream()
                .collect(toMap(assignment -> assignment.getTreatment().getId(),
                        ResponsibleAssignment::getPhysiotherapistId));
        return treatments.stream()
                .sorted(ACTIVE_THEN_MOST_RECENT)
                .map(treatment -> new PatientTreatment(treatment.getId(), patientId, treatment.getStatus().name(),
                        treatment.isActive(), treatment.getStartDate(), treatment.getInitialPainScore(),
                        treatment.getPlannedSessions(), treatment.getDiagnosis().getCode(),
                        treatment.getDiagnosis().getDescription(), responsibleIds.get(treatment.getId())))
                .toList();
    }

    private static TreatmentSummary summaryOf(Treatment treatment) {
        return new TreatmentSummary(treatment.getId(), treatment.getClinicalRecord().getPatientId(),
                treatment.isActive(), treatment.getStartDate(), treatment.getInitialPainScore(),
                treatment.getPlannedSessions(), treatment.getDiagnosis().getCode(),
                treatment.getDiagnosis().getDescription());
    }

}
