package ec.rehabelite.assignments.application;

import static java.util.stream.Collectors.toSet;

import ec.rehabelite.assignments.domain.AvailabilityRange;
import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.ActiveTreatment;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentResponsibles;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.staff.PhysiotherapistSchedule;
import ec.rehabelite.staff.PhysiotherapistSchedules;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The operations director's schedules and assignments (MEJ-04). Every read has a fixed number of statements, whatever
 * the number of physiotherapists or treatments. Schedules and names never carry clinical data; the treatment list
 * carries the patient's name and the start date only, and each listed treatment is audited as READ (HU-18).
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    static final String TREATMENT_ENTITY = "Treatment";

    private final PhysiotherapistSchedules schedules;
    private final StaffDirectory staffDirectory;
    private final TreatmentQueryPort treatmentQueries;
    private final TreatmentResponsibles treatmentResponsibles;
    private final PatientDirectory patientDirectory;
    private final AuditLogService auditLogService;
    private final AppProperties properties;

    /** A physiotherapist of the board: its schedule, whether its account is active and its ACTIVE treatments. */
    public record PhysiotherapistLoad(PhysiotherapistSchedule schedule, boolean active, long activeTreatments) {
    }

    /**
     * A row of the director's treatment list: the treatment, the patient's name, its current responsible (name and
     * whether the account is active) and the days of the responsible's schedule.
     */
    public record TreatmentAssignment(ActiveTreatment treatment, String patientName, String responsibleName,
            boolean responsibleActive, Set<DayOfWeek> scheduleDays) {

        /** The treatment still has a responsible, but its account is deactivated: it needs a new one. */
        public boolean responsibleInactive() {
            return treatment.responsibleId() != null && !responsibleActive;
        }

    }

    /**
     * Every physiotherapist, active or not, with its schedule and load, ordered by last names and first names. Five
     * statements: the physiotherapists, their records and slots, the active accounts and the loads.
     */
    @Transactional(readOnly = true)
    public List<PhysiotherapistLoad> board() {
        Set<Long> active = activePhysiotherapistIds();
        Map<Long, Long> loads = treatmentQueries.activeLoadByResponsible();
        return schedules.all().stream()
                .map(schedule -> new PhysiotherapistLoad(schedule, active.contains(schedule.userId()),
                        loads.getOrDefault(schedule.userId(), 0L)))
                .toList();
    }

    /**
     * The physiotherapists with an active account and one slot that covers the whole range of the day (contiguous
     * slots are already merged), from the smallest load to the largest and then by last names and first names.
     *
     * @throws ec.rehabelite.shared.domain.InvalidRequestException if the range is not valid ({@link AvailabilityRange})
     */
    @Transactional(readOnly = true)
    public List<PhysiotherapistLoad> availability(DayOfWeek day, LocalTime from, LocalTime to) {
        AppProperties.Clinic clinic = properties.clinic();
        AvailabilityRange range = AvailabilityRange.of(day, from, to, clinic.openingTime(), clinic.closingTime());
        Set<Long> active = activePhysiotherapistIds();
        Map<Long, Long> loads = treatmentQueries.activeLoadByResponsible();
        // all() is already ordered by names, and the sort is stable
        return schedules.all().stream()
                .filter(schedule -> active.contains(schedule.userId()))
                .filter(schedule -> schedule.covers(range.day(), range.from(), range.to()))
                .map(schedule -> new PhysiotherapistLoad(schedule, true, loads.getOrDefault(schedule.userId(), 0L)))
                .sorted(Comparator.comparingLong(PhysiotherapistLoad::activeTreatments))
                .toList();
    }

    /**
     * One page of the ACTIVE treatments, the most recent start first; with {@code onlyInactiveResponsible}, only those
     * whose current responsible has a deactivated account. Each listed treatment is audited as READ, in one INSERT.
     */
    @Transactional
    public Page<TreatmentAssignment> treatments(boolean onlyInactiveResponsible, Pageable pageable) {
        Set<Long> active = activePhysiotherapistIds();
        Collection<Long> responsibleIds = onlyInactiveResponsible
                ? staffDirectory.physiotherapists().stream().map(StaffMember::id).filter(id -> !active.contains(id))
                        .toList()
                : null;
        Page<ActiveTreatment> page = treatmentQueries.activeTreatments(responsibleIds, pageable);
        if (page.isEmpty()) {
            return page.map(treatment -> null);
        }
        List<ActiveTreatment> rows = page.getContent();
        List<Long> responsibles = rows.stream().map(ActiveTreatment::responsibleId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> patientNames = patientDirectory.fullNames(
                rows.stream().map(ActiveTreatment::patientId).collect(toSet()));
        Map<Long, String> responsibleNames = staffDirectory.fullNames(responsibles);
        Map<Long, Set<DayOfWeek>> days = schedules.workingDays(responsibles);
        auditLogService.recordAll(AuditAction.READ, TREATMENT_ENTITY,
                rows.stream().map(ActiveTreatment::id).toList());
        return page.map(treatment -> assignmentOf(treatment, patientNames, responsibleNames, active, days));
    }

    /**
     * Makes the physiotherapist the current responsible of the treatment, with the logic of HU-08 (audited as UPDATE
     * Treatment, with an Envers revision). The availability is not required: the dialog only offers the available
     * physiotherapists, and the director may know better (D8).
     *
     * @return the row of the treatment list, with its new responsible
     * @throws ec.rehabelite.shared.domain.ResourceNotFoundException if the treatment does not exist
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the user is not an active physiotherapist or the
     *         treatment is not active
     */
    @Transactional
    public TreatmentAssignment reassign(Long treatmentId, Long physiotherapistId) {
        ActiveTreatment treatment = treatmentResponsibles.reassign(treatmentId, physiotherapistId);
        List<Long> responsible = List.of(physiotherapistId);
        return assignmentOf(treatment, patientDirectory.fullNames(List.of(treatment.patientId())),
                staffDirectory.fullNames(responsible), Set.of(physiotherapistId),
                schedules.workingDays(responsible));
    }

    private Set<Long> activePhysiotherapistIds() {
        return staffDirectory.activePhysiotherapists().stream().map(StaffMember::id).collect(toSet());
    }

    private static TreatmentAssignment assignmentOf(ActiveTreatment treatment, Map<Long, String> patientNames,
            Map<Long, String> responsibleNames, Set<Long> active, Map<Long, Set<DayOfWeek>> days) {
        Long responsibleId = treatment.responsibleId();
        return new TreatmentAssignment(treatment, patientNames.get(treatment.patientId()),
                responsibleId == null ? null : responsibleNames.get(responsibleId),
                responsibleId != null && active.contains(responsibleId),
                responsibleId == null ? Set.of() : days.getOrDefault(responsibleId, Set.of()));
    }

}
