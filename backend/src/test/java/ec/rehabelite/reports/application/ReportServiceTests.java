package ec.rehabelite.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.reports.application.ReportService.ReportFile;
import ec.rehabelite.reports.application.ReportService.ReportFilter;
import ec.rehabelite.reports.application.ReportService.ReportTreatment;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.reports.domain.OperationsSummary.PhysiotherapistSessions;
import ec.rehabelite.reports.domain.OperationsSummary.TreatmentCounts;
import ec.rehabelite.sessions.SessionCount;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import ec.rehabelite.shared.domain.InvalidRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** HU-14, HU-15: what the report service asks the other modules for, and what it audits. */
class ReportServiceTests {

    private static final Long VALERIA = 3L;
    private static final Long MATEO = 4L;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);
    private static final ReportFilter NO_FILTER = new ReportFilter(null, null, null, null);
    private static final LocalDate OCTOBER_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCTOBER_31 = LocalDate.of(2026, 10, 31);
    /** 14:35 in Quito; in UTC it is 19:35. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T19:35:00Z"), ZoneOffset.UTC);

    private final TreatmentQueryPort treatmentQuery = mock(TreatmentQueryPort.class);
    private final TherapySessionQueryPort sessionQuery = mock(TherapySessionQueryPort.class);
    private final PatientDirectory patientDirectory = mock(PatientDirectory.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final OperationsReportPdf pdf = mock(OperationsReportPdf.class);

    private final ReportService service = new ReportService(treatmentQuery, sessionQuery, patientDirectory,
            staffDirectory, currentUserProvider, auditLogService, pdf, CLOCK);

    @Test
    void theTreatmentsAreFilteredByTheirResponsibleAndTheSessionsByWhoRegisteredThem() {
        when(treatmentQuery.countByStatus(new TreatmentFilter("ACTIVE", VALERIA)))
                .thenReturn(Map.of("ACTIVE", 2L, "COMPLETED", 0L, "ABANDONED", 0L));
        // The sessions: of every treatment in the status, whoever its current responsible is
        when(treatmentQuery.idsWithStatus("ACTIVE")).thenReturn(List.of(10L, 20L, 30L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(10L, 20L, 30L), VALERIA, null, null))
                .thenReturn(List.of(new SessionCount(VALERIA, "COMPLETED", 7)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade"));

        OperationsSummary summary = service.summary(new ReportFilter("ACTIVE", VALERIA, null, null));

        assertThat(summary.treatments()).isEqualTo(new TreatmentCounts(2, 0, 0));
        assertThat(summary.sessions().total()).isEqualTo(7);
        assertThat(summary.sessionsByPhysiotherapist())
                .containsExactly(new PhysiotherapistSessions(VALERIA, "Valeria Andrade", 7));
    }

    @Test
    void withoutFilterTheSessionsOfEveryTreatmentAndEveryPhysiotherapistAreCounted() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of("ACTIVE", 1L));
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of(10L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(10L), null, null, null)).thenReturn(List.of(
                new SessionCount(VALERIA, "COMPLETED", 3), new SessionCount(MATEO, "NO_SHOW", 1)));
        when(staffDirectory.fullNames(Set.of(VALERIA, MATEO)))
                .thenReturn(Map.of(VALERIA, "Valeria Andrade", MATEO, "Mateo Ríos"));

        OperationsSummary summary = service.summary(NO_FILTER);

        assertThat(summary.sessionsByPhysiotherapist()).extracting(PhysiotherapistSessions::name)
                .containsExactly("Valeria Andrade", "Mateo Ríos");
        assertThat(summary.averageSessionsPerPhysiotherapist()).isEqualByComparingTo("2.0");
    }

    @Test
    void theSummaryIsAuditedAsOneReadOfTheReport() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of());
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of());
        when(sessionQuery.countsByResponsibleAndStatus(List.of(), null, null, null)).thenReturn(List.of());

        OperationsSummary summary = service.summary(NO_FILTER);

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

        Page<ReportTreatment> page = service.treatments(new ReportFilter("ACTIVE", null, null, null), FIRST_PAGE);

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

        assertThat(service.treatments(new ReportFilter(null, MATEO, null, null), FIRST_PAGE)).isEmpty();
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

    @Test
    void aRangeWhoseStartIsAfterItsEndIsRejectedBeforeReadingAnything() {
        assertThatThrownBy(() -> new ReportFilter(null, null, OCTOBER_31, OCTOBER_1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la fecha final.");
        // One day, and either bound alone, are valid ranges
        assertThat(new ReportFilter(null, null, OCTOBER_1, OCTOBER_1).hasRange()).isTrue();
        assertThat(new ReportFilter(null, null, OCTOBER_1, null).hasRange()).isTrue();
        assertThat(new ReportFilter(null, null, null, OCTOBER_31).hasRange()).isTrue();
        assertThat(NO_FILTER.hasRange()).isFalse();
    }

    @Test
    void theRangeFiltersTheTreatmentsByTheirPeriodAndTheSessionsByTheirDate() {
        ReportFilter october = new ReportFilter("ACTIVE", VALERIA, OCTOBER_1, OCTOBER_31);
        when(treatmentQuery.countByStatus(new TreatmentFilter("ACTIVE", VALERIA, OCTOBER_1, OCTOBER_31)))
                .thenReturn(Map.of("ACTIVE", 1L));
        // The sessions: of every treatment in the status, whatever its period; the range bounds their date
        when(treatmentQuery.idsWithStatus("ACTIVE")).thenReturn(List.of(10L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(10L), VALERIA, OCTOBER_1, OCTOBER_31))
                .thenReturn(List.of(new SessionCount(VALERIA, "COMPLETED", 4)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade"));
        when(treatmentQuery.search(new TreatmentFilter("ACTIVE", VALERIA, OCTOBER_1, OCTOBER_31), FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        assertThat(service.summary(october).sessions().total()).isEqualTo(4);
        assertThat(service.treatments(october, FIRST_PAGE)).isEmpty();
    }

    @Test
    void theExportRunsTheQueriesOfTheViewWithEveryTreatmentAndLooksTheStaffUpInOneCall() {
        ReportFilter filter = new ReportFilter(null, MATEO, OCTOBER_1, OCTOBER_31);
        TreatmentFilter treatments = new TreatmentFilter(null, MATEO, OCTOBER_1, OCTOBER_31);
        when(treatmentQuery.countByStatus(treatments)).thenReturn(Map.of("ACTIVE", 2L));
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of(30L, 20L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(30L, 20L), MATEO, OCTOBER_1, OCTOBER_31))
                .thenReturn(List.of(new SessionCount(VALERIA, "COMPLETED", 3)));
        // Not one page: every treatment of the filter
        when(treatmentQuery.list(treatments)).thenReturn(List.of(listed(30L, 300L, MATEO), listed(20L, 200L, null)));
        when(patientDirectory.fullNames(Set.of(300L, 200L)))
                .thenReturn(Map.of(300L, "Rosa Elena Ortiz Vega", 200L, "Luis Alberto Quishpe Tipán"));
        when(staffDirectory.fullNames(Set.of(VALERIA, MATEO)))
                .thenReturn(Map.of(VALERIA, "Valeria Andrade", MATEO, "Mateo Ríos"));
        when(currentUserProvider.currentUser())
                .thenReturn(Optional.of(new CurrentUser(9L, "d@rehabelite.test", "Dirección Operativa",
                        Role.OPERATIONS_DIRECTOR)));
        when(pdf.render(any())).thenReturn(new byte[] {1, 2, 3});

        ReportFile file = service.export(filter);

        assertThat(file.content()).containsExactly(1, 2, 3);
        assertThat(file.fileName()).isEqualTo("informe-operativo-2026-11-16.pdf");
        ArgumentCaptor<OperationsReportPdf.Content> content =
                ArgumentCaptor.forClass(OperationsReportPdf.Content.class);
        verify(pdf).render(content.capture());
        assertThat(content.getValue().filter()).isEqualTo(filter);
        assertThat(content.getValue().physiotherapistName()).isEqualTo("Mateo Ríos");
        assertThat(content.getValue().summary().treatments()).isEqualTo(new TreatmentCounts(2, 0, 0));
        assertThat(content.getValue().summary().sessionsByPhysiotherapist())
                .containsExactly(new PhysiotherapistSessions(VALERIA, "Valeria Andrade", 3));
        assertThat(content.getValue().treatments())
                .extracting(ReportTreatment::patientName, ReportTreatment::responsibleName)
                .containsExactly(tuple("Rosa Elena Ortiz Vega", "Mateo Ríos"),
                        tuple("Luis Alberto Quishpe Tipán", null));
        assertThat(content.getValue().generatedBy()).isEqualTo("Dirección Operativa");
        assertThat(content.getValue().generatedAt().toLocalDateTime()).isEqualTo("2026-11-16T14:35:00");
        verify(treatmentQuery, never()).search(any(), any());
    }

    @Test
    void theExportIsAuditedAsOneExportOfTheReportAndOneReadPerTreatment() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of());
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of(30L, 20L));
        when(sessionQuery.countsByResponsibleAndStatus(List.of(30L, 20L), null, null, null)).thenReturn(List.of());
        when(treatmentQuery.list(TreatmentFilter.ALL))
                .thenReturn(List.of(listed(30L, 300L, null), listed(20L, 200L, null)));
        when(currentUserProvider.currentUser()).thenReturn(Optional.empty());
        when(pdf.render(any())).thenReturn(new byte[0]);

        service.export(NO_FILTER);

        InOrder inOrder = inOrder(pdf, auditLogService);
        inOrder.verify(pdf).render(any());
        inOrder.verify(auditLogService).record(AuditAction.EXPORT, "Report", null);
        inOrder.verify(auditLogService).recordAll(AuditAction.READ, "Treatment", List.of(30L, 20L));
        verifyNoMoreInteractions(auditLogService);
    }

    @Test
    void aPdfThatCannotBeBuiltLeavesNoAuditEntries() {
        when(treatmentQuery.countByStatus(TreatmentFilter.ALL)).thenReturn(Map.of());
        when(treatmentQuery.idsWithStatus(null)).thenReturn(List.of());
        when(sessionQuery.countsByResponsibleAndStatus(List.of(), null, null, null)).thenReturn(List.of());
        when(treatmentQuery.list(TreatmentFilter.ALL)).thenReturn(List.of());
        when(currentUserProvider.currentUser()).thenReturn(Optional.empty());
        when(pdf.render(any())).thenThrow(new UncheckedIOException(new IOException("disk full")));

        assertThatThrownBy(() -> service.export(NO_FILTER)).isInstanceOf(UncheckedIOException.class);
        verifyNoInteractions(auditLogService);
    }

    private static PatientTreatment listed(Long id, Long patientId, Long responsibleId) {
        return new PatientTreatment(id, patientId, "ACTIVE", true, LocalDate.of(2026, 10, 1), 7, 12, "M54.5",
                "Lumbago no especificado", responsibleId);
    }

}
