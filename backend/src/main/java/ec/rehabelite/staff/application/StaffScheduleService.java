package ec.rehabelite.staff.application;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import ec.rehabelite.staff.PhysiotherapistSchedule;
import ec.rehabelite.staff.PhysiotherapistSchedules;
import ec.rehabelite.staff.ScheduleSlot;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import ec.rehabelite.staff.domain.StaffScheduleSlot;
import ec.rehabelite.staff.domain.StaffScheduleSlotRepository;
import ec.rehabelite.staff.domain.WeeklySchedule;
import ec.rehabelite.staff.domain.WorkingDay;
import java.text.Collator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly schedules of the physiotherapists (MEJ-04): the administrator and the operations director read and replace
 * them, a physiotherapist reads its own, and the module assignments reads them all through
 * {@link PhysiotherapistSchedules}. Only the record of a physiotherapist's account has a schedule.
 */
@Service
@RequiredArgsConstructor
public class StaffScheduleService implements PhysiotherapistSchedules {

    static final String NOT_A_PHYSIOTHERAPIST = "Solo los fisioterapeutas tienen horario.";
    static final String NO_STAFF_RECORD = "Su cuenta no tiene un registro del personal.";

    private static final Logger log = LoggerFactory.getLogger(StaffScheduleService.class);

    private final StaffRecordRepository staffRepository;
    private final StaffScheduleSlotRepository slotRepository;
    private final AccountProvisioning accounts;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final AppProperties properties;
    private final Clock clock;

    /** A staff record with its slots, ordered by day and start time. */
    public record StaffSchedule(StaffRecord record, List<ScheduleSlot> slots) {
    }

    /**
     * The schedule of a physiotherapist's record. Not audited: it is not clinical data.
     *
     * @throws ResourceNotFoundException if the record does not exist
     * @throws BusinessRuleException if the record is not a physiotherapist's
     */
    @Transactional(readOnly = true)
    public StaffSchedule schedule(Long staffId) {
        StaffRecord record = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException(StaffRegistrationService.STAFF_NOT_FOUND));
        requirePhysiotherapist(record);
        return new StaffSchedule(record, slotsOf(record.getId()));
    }

    /**
     * Replaces the whole week with the requested slots, checked and with the contiguous ones merged
     * ({@link WeeklySchedule}). The record stays locked until the end, so two replacements run one after the other.
     * Sending the schedule it already has changes and audits nothing; a real change is audited as UPDATE
     * StaffRecord.
     *
     * @return the saved schedule, as {@link #schedule(Long)} returns it
     * @throws ResourceNotFoundException if the record does not exist
     * @throws BusinessRuleException if the record is not a physiotherapist's
     * @throws ec.rehabelite.shared.domain.InvalidRequestException if a slot breaks a rule of {@link WeeklySchedule}
     */
    @Transactional
    public StaffSchedule replace(Long staffId, List<ScheduleSlot> requested) {
        StaffRecord record = staffRepository.findForUpdate(staffId)
                .orElseThrow(() -> new ResourceNotFoundException(StaffRegistrationService.STAFF_NOT_FOUND));
        requirePhysiotherapist(record);
        AppProperties.Clinic clinic = properties.clinic();
        List<ScheduleSlot> slots = WeeklySchedule.of(requested, clinic.openingTime(), clinic.closingTime()).slots();
        if (slots.equals(slotsOf(record.getId()))) {
            return new StaffSchedule(record, slots);
        }

        Long userId = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        slotRepository.deleteByStaffRecordId(record.getId());
        slotRepository.saveAll(slots.stream().map(slot -> StaffScheduleSlot.of(record.getId(), slot, userId, now))
                .toList());
        auditLogService.record(AuditAction.UPDATE, StaffRegistrationService.AUDITED_ENTITY, record.getId());
        log.info("Staff schedule replaced staffId={} slots={} updatedBy={}", record.getId(), slots.size(), userId);
        return new StaffSchedule(record, slots);
    }

    /**
     * The schedule of the authenticated physiotherapist, identified by the token, never by a parameter.
     *
     * @throws ResourceNotFoundException if the account has no staff record
     */
    @Transactional(readOnly = true)
    public StaffSchedule ownSchedule() {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("The own schedule needs an authenticated user"));
        StaffRecord record = staffRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NO_STAFF_RECORD));
        return new StaffSchedule(record, slotsOf(record.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhysiotherapistSchedule> all() {
        List<Long> physiotherapists = accounts.idsWithRole(Role.PHYSIOTHERAPIST);
        if (physiotherapists.isEmpty()) {
            return List.of();
        }
        List<StaffRecord> records = staffRepository.findByUserIdIn(physiotherapists);
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ScheduleSlot>> slots = slotRepository
                .findByStaffRecordIds(records.stream().map(StaffRecord::getId).toList()).stream()
                .collect(groupingBy(StaffScheduleSlot::getStaffRecordId,
                        mapping(StaffScheduleSlot::toSlot, toList())));
        Collator spanish = Collator.getInstance(Locale.forLanguageTag("es-EC"));
        return records.stream()
                .map(record -> new PhysiotherapistSchedule(record.getId(), record.getUserId(), record.getFirstName(),
                        record.getLastName(), slots.getOrDefault(record.getId(), List.of())))
                .sorted(Comparator.comparing(PhysiotherapistSchedule::lastName, spanish)
                        .thenComparing(PhysiotherapistSchedule::firstName, spanish)
                        .thenComparing(PhysiotherapistSchedule::staffId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Set<DayOfWeek>> workingDays(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findWorkingDays(userIds).stream()
                .collect(groupingBy(WorkingDay::userId, mapping(day -> DayOfWeek.of(day.dayOfWeek()),
                        toCollection(() -> EnumSet.noneOf(DayOfWeek.class)))));
    }

    private void requirePhysiotherapist(StaffRecord record) {
        boolean physiotherapist = accounts.summary(record.getUserId())
                .map(AccountSummary::role)
                .filter(role -> role == Role.PHYSIOTHERAPIST)
                .isPresent();
        if (!physiotherapist) {
            throw new BusinessRuleException(NOT_A_PHYSIOTHERAPIST);
        }
    }

    private List<ScheduleSlot> slotsOf(Long staffRecordId) {
        return slotRepository.findByStaffRecordIdOrderByDayOfWeekAscStartTimeAsc(staffRecordId).stream()
                .map(StaffScheduleSlot::toSlot)
                .toList();
    }

}
