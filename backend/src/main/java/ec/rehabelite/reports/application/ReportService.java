package ec.rehabelite.reports.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.sessions.SessionCount;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only operations reports for the operations director (HU-14). Treatments, sessions, patients and staff are read
 * only through the public APIs of their modules.
 * <p>
 * The filter applies differently to each part of the report:
 * <ul>
 * <li>treatments (the counts per status and the list): by their status and by their current responsible;</li>
 * <li>sessions (per status, per physiotherapist and the average): by who registered them and by the status of the
 * treatment they belong to. Filtering by a physiotherapist shows only the sessions they registered, also in
 * treatments whose current responsible is now someone else.</li>
 * </ul>
 * Every read is audited (HU-18), so the methods are transactional without readOnly.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final String REPORT_ENTITY = "Report";
    static final String TREATMENT_ENTITY = "Treatment";

    private final TreatmentQueryPort treatmentQuery;
    private final TherapySessionQueryPort sessionQuery;
    private final PatientDirectory patientDirectory;
    private final StaffDirectory staffDirectory;
    private final AuditLogService auditLogService;

    /**
     * Criteria of the report; both are optional.
     *
     * @param treatmentStatus   value of treatments.status: ACTIVE, COMPLETED or ABANDONED; null for every status
     * @param physiotherapistId for the treatments, their current responsible; for the sessions, who registered them
     */
    public record ReportFilter(String treatmentStatus, Long physiotherapistId) {
    }

    /**
     * A treatment of the list with the names of its patient and of its current responsible (null if it has none).
     * Never the national ID nor other personal data of the patient.
     */
    public record ReportTreatment(PatientTreatment treatment, String patientName, String responsibleName) {
    }

    /**
     * The indicators of the report, audited as one READ of the report. Six statements, whatever the number of
     * treatments and sessions: token check, treatments per status, ids of the treatments in the status, sessions per
     * physiotherapist and status, names, and the audit entry.
     */
    @Transactional
    public OperationsSummary summary(ReportFilter filter) {
        Map<String, Long> treatments = treatmentQuery.countByStatus(
                new TreatmentFilter(filter.treatmentStatus(), filter.physiotherapistId()));
        List<SessionCount> sessions = sessionQuery.countsByResponsibleAndStatus(
                treatmentQuery.idsWithStatus(filter.treatmentStatus()), filter.physiotherapistId());
        Set<Long> physiotherapistIds = sessions.stream()
                .map(SessionCount::responsibleId)
                .collect(Collectors.toSet());
        Map<Long, String> names = physiotherapistIds.isEmpty() ? Map.of()
                : staffDirectory.fullNames(physiotherapistIds);

        auditLogService.record(AuditAction.READ, REPORT_ENTITY, null);
        return OperationsSummary.of(treatments, sessions, names);
    }

    /**
     * One page of the treatments of the filter, from the most recent start date to the oldest. Each treatment listed
     * is audited as READ, all in one INSERT. Six statements, whatever the number of treatments: token check, page,
     * total, names of the patients, names of the responsibles and the audit entries.
     */
    @Transactional
    public Page<ReportTreatment> treatments(ReportFilter filter, Pageable pageable) {
        Page<PatientTreatment> page = treatmentQuery.search(
                new TreatmentFilter(filter.treatmentStatus(), filter.physiotherapistId()), pageable);
        List<PatientTreatment> treatments = page.getContent();
        Map<Long, String> patientNames = patientDirectory.fullNames(
                treatments.stream().map(PatientTreatment::patientId).collect(Collectors.toSet()));
        Map<Long, String> responsibleNames = staffDirectory.fullNames(treatments.stream()
                .map(PatientTreatment::responsibleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        auditLogService.recordAll(AuditAction.READ, TREATMENT_ENTITY,
                treatments.stream().map(PatientTreatment::id).toList());
        return page.map(treatment -> new ReportTreatment(treatment, patientNames.get(treatment.patientId()),
                treatment.responsibleId() == null ? null : responsibleNames.get(treatment.responsibleId())));
    }

    /** Every physiotherapist, active or not, for the filter of the report. Staff data: not audited. */
    @Transactional(readOnly = true)
    public List<StaffMember> physiotherapists() {
        return staffDirectory.physiotherapists();
    }

}
