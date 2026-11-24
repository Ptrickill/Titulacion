package ec.rehabelite.clinicalrecords.application;

import static java.util.stream.Collectors.toMap;

import ec.rehabelite.clinicalrecords.ActiveTreatment;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.ResponsibleLoad;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentListRow;
import ec.rehabelite.clinicalrecords.domain.TreatmentReportQueries.ReportCriteria;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus(TreatmentFilter filter) {
        Map<TreatmentStatus, Long> found = treatmentRepository.countByStatus(criteriaOf(filter));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TreatmentStatus status : TreatmentStatus.values()) {
            counts.put(status.name(), found.getOrDefault(status, 0L));
        }
        return Collections.unmodifiableMap(counts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsWithStatus(String status) {
        return treatmentRepository.findIdsByStatus(statusOf(status));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientTreatment> search(TreatmentFilter filter, Pageable pageable) {
        return treatmentRepository.findReportPage(criteriaOf(filter), pageable)
                .map(TreatmentQueryService::patientTreatmentOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientTreatment> list(TreatmentFilter filter) {
        return treatmentRepository.findReportRows(criteriaOf(filter)).stream()
                .map(TreatmentQueryService::patientTreatmentOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> activeLoadByResponsible() {
        return treatmentRepository.countByCurrentResponsible(TreatmentStatus.ACTIVE).stream()
                .collect(toMap(ResponsibleLoad::physiotherapistId, ResponsibleLoad::treatments));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActiveTreatment> activeTreatments(Collection<Long> responsibleIds, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (responsibleIds == null) {
            return treatmentRepository.findWithCurrentResponsible(TreatmentStatus.ACTIVE, unsorted);
        }
        if (responsibleIds.isEmpty()) {
            return Page.empty(unsorted);
        }
        return treatmentRepository.findWithCurrentResponsibleIn(TreatmentStatus.ACTIVE, responsibleIds, unsorted);
    }

    private static ReportCriteria criteriaOf(TreatmentFilter filter) {
        return new ReportCriteria(statusOf(filter.status()), filter.responsibleId(), filter.from(), filter.to());
    }

    private static PatientTreatment patientTreatmentOf(TreatmentListRow row) {
        return new PatientTreatment(row.id(), row.patientId(), row.status().name(),
                row.status() == TreatmentStatus.ACTIVE, row.startDate(), row.initialPainScore(),
                row.plannedSessions(), row.diagnosisCode(), row.diagnosisDescription(), row.responsibleId());
    }

    /** Null means every status. */
    private static TreatmentStatus statusOf(String status) {
        return status == null ? null : TreatmentStatus.valueOf(status);
    }

    private static TreatmentSummary summaryOf(Treatment treatment) {
        return new TreatmentSummary(treatment.getId(), treatment.getClinicalRecord().getPatientId(),
                treatment.isActive(), treatment.getStartDate(), treatment.getInitialPainScore(),
                treatment.getPlannedSessions(), treatment.getDiagnosis().getCode(),
                treatment.getDiagnosis().getDescription());
    }

}
