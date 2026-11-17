package ec.rehabelite.reports.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.sessions.SessionCount;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
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
 * Read-only operations reports for the operations director (HU-14, HU-15). Treatments, sessions, patients and staff
 * are read only through the public APIs of their modules.
 * <p>
 * The filter applies differently to each part of the report:
 * <ul>
 * <li>treatments (the counts per status and the list): by their status, by their current responsible and by their
 * period (in progress some day of the range);</li>
 * <li>sessions (per status, per physiotherapist and the average): by who registered them, by the status of the
 * treatment they belong to and by their date. Filtering by a physiotherapist shows only the sessions they
 * registered, also in treatments whose current responsible is now someone else.</li>
 * </ul>
 * The PDF (HU-15) runs exactly the queries of the view: the indicators of {@link #summary} and every treatment of
 * {@link #treatments} instead of one page. Every read is audited (HU-18), so the methods are transactional without
 * readOnly.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final String REPORT_ENTITY = "Report";
    static final String TREATMENT_ENTITY = "Treatment";
    static final String INVALID_RANGE = "La fecha inicial no puede ser posterior a la fecha final.";

    private final TreatmentQueryPort treatmentQuery;
    private final TherapySessionQueryPort sessionQuery;
    private final PatientDirectory patientDirectory;
    private final StaffDirectory staffDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final OperationsReportPdf pdf;
    private final Clock clock;

    /**
     * Criteria of the report; all are optional.
     *
     * @param treatmentStatus   value of treatments.status: ACTIVE, COMPLETED or ABANDONED; null for every status
     * @param physiotherapistId for the treatments, their current responsible; for the sessions, who registered them
     * @param from              first day of the range, in Quito, included
     * @param to                last day of the range, in Quito, included
     * @throws InvalidRequestException if {@code from} is after {@code to}, before anything is read
     */
    public record ReportFilter(String treatmentStatus, Long physiotherapistId, LocalDate from, LocalDate to) {

        public ReportFilter {
            if (from != null && to != null && from.isAfter(to)) {
                throw new InvalidRequestException(INVALID_RANGE);
            }
        }

        /** Whether a date range is applied. */
        public boolean hasRange() {
            return from != null || to != null;
        }

        private TreatmentFilter treatments() {
            return new TreatmentFilter(treatmentStatus, physiotherapistId, from, to);
        }

    }

    /**
     * A treatment of the list with the names of its patient and of its current responsible (null if it has none).
     * Never the national ID nor other personal data of the patient.
     */
    public record ReportTreatment(PatientTreatment treatment, String patientName, String responsibleName) {
    }

    /** The PDF of the report and the name of its file. */
    public record ReportFile(String fileName, byte[] content) {
    }

    /**
     * The indicators of the report, audited as one READ of the report. Six statements, whatever the number of
     * treatments and sessions: token check, treatments per status, ids of the treatments in the status, sessions per
     * physiotherapist and status, names, and the audit entry.
     */
    @Transactional
    public OperationsSummary summary(ReportFilter filter) {
        Indicators indicators = indicators(filter);
        Set<Long> physiotherapistIds = indicators.physiotherapistIds();
        Map<Long, String> names = physiotherapistIds.isEmpty() ? Map.of()
                : staffDirectory.fullNames(physiotherapistIds);

        auditLogService.record(AuditAction.READ, REPORT_ENTITY, null);
        return indicators.withNames(names);
    }

    /**
     * One page of the treatments of the filter, from the most recent start date to the oldest. Each treatment listed
     * is audited as READ, all in one INSERT. Six statements, whatever the number of treatments: token check, page,
     * total, names of the patients, names of the responsibles and the audit entries.
     */
    @Transactional
    public Page<ReportTreatment> treatments(ReportFilter filter, Pageable pageable) {
        Page<PatientTreatment> page = treatmentQuery.search(filter.treatments(), pageable);
        List<PatientTreatment> treatments = page.getContent();
        Map<Long, String> patientNames = patientNames(treatments);
        Map<Long, String> responsibleNames = staffDirectory.fullNames(responsibleIds(treatments));

        auditLogService.recordAll(AuditAction.READ, TREATMENT_ENTITY, idsOf(treatments));
        return page.map(treatment -> listed(treatment, patientNames, responsibleNames));
    }

    /**
     * The report as a PDF (HU-15), with the same queries as the view: the indicators of {@link #summary} and every
     * treatment of {@link #treatments}. Audited as one EXPORT of the report and one READ per treatment of the PDF.
     * The PDF is built inside the transaction, so a failure leaves no audit entries. Nine statements, whatever the
     * number of treatments: token check, treatments per status, ids, sessions, treatments, names of the patients,
     * names of the staff (authors of the sessions, responsibles and the one of the filter, in one call) and the two
     * INSERTs.
     */
    @Transactional
    public ReportFile export(ReportFilter filter) {
        Indicators indicators = indicators(filter);
        List<PatientTreatment> treatments = treatmentQuery.list(filter.treatments());
        Map<Long, String> patientNames = patientNames(treatments);

        Set<Long> staffIds = new HashSet<>(indicators.physiotherapistIds());
        staffIds.addAll(responsibleIds(treatments));
        if (filter.physiotherapistId() != null) {
            staffIds.add(filter.physiotherapistId());
        }
        Map<Long, String> staffNames = staffIds.isEmpty() ? Map.of() : staffDirectory.fullNames(staffIds);

        List<ReportTreatment> rows = treatments.stream()
                .map(treatment -> listed(treatment, patientNames, staffNames))
                .toList();
        ZonedDateTime generatedAt = ZonedDateTime.now(clock.withZone(ClinicCalendar.ZONE));
        String generatedBy = currentUserProvider.currentUser().map(CurrentUser::fullName).orElse(null);
        String physiotherapistName = filter.physiotherapistId() == null ? null
                : staffNames.get(filter.physiotherapistId());

        byte[] content = pdf.render(new OperationsReportPdf.Content(filter, physiotherapistName,
                indicators.withNames(staffNames), rows, generatedAt, generatedBy));
        auditLogService.record(AuditAction.EXPORT, REPORT_ENTITY, null);
        auditLogService.recordAll(AuditAction.READ, TREATMENT_ENTITY, idsOf(treatments));
        return new ReportFile("informe-operativo-" + generatedAt.toLocalDate() + ".pdf", content);
    }

    /** Every physiotherapist, active or not, for the filter of the report. Staff data: not audited. */
    @Transactional(readOnly = true)
    public List<StaffMember> physiotherapists() {
        return staffDirectory.physiotherapists();
    }

    /** Treatments per status, and the sessions per author and status: three statements. */
    private Indicators indicators(ReportFilter filter) {
        Map<String, Long> treatments = treatmentQuery.countByStatus(filter.treatments());
        List<SessionCount> sessions = sessionQuery.countsByResponsibleAndStatus(
                treatmentQuery.idsWithStatus(filter.treatmentStatus()), filter.physiotherapistId(), filter.from(),
                filter.to());
        return new Indicators(treatments, sessions);
    }

    private Map<Long, String> patientNames(List<PatientTreatment> treatments) {
        return patientDirectory.fullNames(
                treatments.stream().map(PatientTreatment::patientId).collect(Collectors.toSet()));
    }

    private static Set<Long> responsibleIds(List<PatientTreatment> treatments) {
        return treatments.stream()
                .map(PatientTreatment::responsibleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static List<Long> idsOf(List<PatientTreatment> treatments) {
        return treatments.stream().map(PatientTreatment::id).toList();
    }

    private static ReportTreatment listed(PatientTreatment treatment, Map<Long, String> patientNames,
            Map<Long, String> staffNames) {
        return new ReportTreatment(treatment, patientNames.get(treatment.patientId()),
                treatment.responsibleId() == null ? null : staffNames.get(treatment.responsibleId()));
    }

    private record Indicators(Map<String, Long> treatments, List<SessionCount> sessions) {

        Set<Long> physiotherapistIds() {
            return sessions.stream().map(SessionCount::responsibleId).collect(Collectors.toSet());
        }

        OperationsSummary withNames(Map<Long, String> names) {
            return OperationsSummary.of(treatments, sessions, names);
        }

    }

}
