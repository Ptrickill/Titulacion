package ec.rehabelite.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.reports.application.ReportService.ReportFilter;
import ec.rehabelite.reports.application.ReportService.ReportTreatment;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.reports.domain.OperationsSummary.PhysiotherapistSessions;
import ec.rehabelite.reports.domain.OperationsSummary.TreatmentCounts;
import ec.rehabelite.sessions.SessionCount;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** HU-14: what the report service asks the other modules for, and what it audits. */
class ReportServiceTests {

    private static final Long VALERIA = 3L;
    private static final Long MATEO = 4L;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    private final TreatmentQueryPort treatmentQuery = mock(TreatmentQueryPort.class);
    private final TherapySessionQueryPort sessionQuery = mock(TherapySessionQueryPort.class);
    private final PatientDirectory patientDirectory = mock(PatientDirectory.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final ReportService service = new ReportService(treatmentQuery, sessionQuery, patientDirectory,
            staffDirectory, auditLogService);

    @Test
    void theTreatmentsAreFilteredByTheirResponsibleAndTheSessionsByWhoRegisteredThem() {
        when(treatmentQuery.countByStatus(new TreatmentFilter("ACTIVE", VALERIA)))
                .thenReturn(Map.of("ACTIVE", 2L, "COMPLETED", 0L, "ABANDONED", 0L));
        // The sessions: of every treatment in the status, whoever its current responsible is
        when(treatmentQuery.idsWithStatus("ACTIVE")).thenReturn(List.of(10L, 20L, 30L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(10L, 20L, 30L), VALERIA))
                .thenReturn(List.of(new SessionCount(VALERIA, "COMPLETED", 7)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade"));

        OperationsSummary summary = service.summary(new ReportFilter("ACTIVE", VALERIA));

        assertThat(summary.treatments()).isEqualTo(new TreatmentCounts(2, 0, 0));
        assertThat(summary.sessions().total()).isEqualTo(7);
        assertThat(summary.sessionsByPhysiotherapist())
                .containsExactly(new PhysiotherapistSessions(VALERIA, "Valeria Andrade", 7));
    }

    @Test
    void withoutFilterTheSessionsOfEveryTreatmentAndEveryPhysiotherapistAreCounted() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of("ACTIVE", 1L));
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of(10L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(10L), null)).thenReturn(List.of(
                new SessionCount(VALERIA, "COMPLETED", 3), new SessionCount(MATEO, "NO_SHOW", 1)));
        when(staffDirectory.fullNames(Set.of(VALERIA, MATEO)))
                .thenReturn(Map.of(VALERIA, "Valeria Andrade", MATEO, "Mateo Ríos"));

        OperationsSummary summary = service.summary(new ReportFilter(null, null));

        assertThat(summary.sessionsByPhysiotherapist()).extracting(PhysiotherapistSessions::name)
                .containsExactly("Valeria Andrade", "Mateo Ríos");
        assertThat(summary.averageSessionsPerPhysiotherapist()).isEqualByComparingTo("2.0");
    }

    @Test
    void theSummaryIsAuditedAsOneReadOfTheReport() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of());
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of());
        when(sessionQuery.countsByResponsibleAndStatus(List.of(), null)).thenReturn(List.of());

        OperationsSummary summary = service.summary(new ReportFilter(null, null));

        verify(auditLogService).record(AuditAction.READ, "Report", null);
        // Without sessions there are no names to look up
        verifyNoInteractions(staffDirectory);
        assertThat(summary.sessionsByPhysiotherapist()).isEmpty();
    }

    @Test
    void theListCarriesTheNamesOfThePatientAndOfTheResponsibleAndAuditsEveryTreatmentInOneCall() {
        PatientTreatment withResponsible = listed(30L, 300L, VALERIA);
        PatientTreatment withoutResponsible = listed(20L, 200L, null);
        when(treatmentQuery.search(new TreatmentFilter("ACTIVE", null), FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(withResponsible, withoutResponsible), FIRST_PAGE, 12));
        when(patientDirectory.fullNames(Set.of(300L, 200L)))
                .thenReturn(Map.of(300L, "Rosa Elena Ortiz Vega", 200L, "Luis Alberto Quishpe Tipán"));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade"));

        Page<ReportTreatment> page = service.treatments(new ReportFilter("ACTIVE", null), FIRST_PAGE);

        assertThat(page.getContent()).extracting(ReportTreatment::patientName)
                .containsExactly("Rosa Elena Ortiz Vega", "Luis Alberto Quishpe Tipán");
        assertThat(page.getContent()).extracting(ReportTreatment::responsibleName)
                .containsExactly("Valeria Andrade", null);
        assertThat(page.getTotalElements()).isEqualTo(12);
        verify(auditLogService).recordAll(AuditAction.READ, "Treatment", List.of(30L, 20L));
    }

    @Test
    void theListOfAResponsibleOnlyAsksForTheirTreatments() {
        when(treatmentQuery.search(new TreatmentFilter(null, MATEO), FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        assertThat(service.treatments(new ReportFilter(null, MATEO), FIRST_PAGE)).isEmpty();
        verify(auditLogService).recordAll(AuditAction.READ, "Treatment", List.of());
    }

    @Test
    void theOptionsOfTheFilterAreEveryPhysiotherapistAndAreNotAudited() {
        List<StaffMember> physiotherapists = List.of(new StaffMember(VALERIA, "Valeria Andrade"),
                new StaffMember(MATEO, "Mateo Ríos"));
        when(staffDirectory.physiotherapists()).thenReturn(physiotherapists);

        assertThat(service.physiotherapists()).isEqualTo(physiotherapists);
        verifyNoInteractions(auditLogService);
    }

    private static PatientTreatment listed(Long id, Long patientId, Long responsibleId) {
        return new PatientTreatment(id, patientId, "ACTIVE", true, LocalDate.of(2026, 10, 1), 7, 12, "M54.5",
                "Lumbago no especificado", responsibleId);
    }

}
