package ec.rehabelite.staff.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StaffScheduleSlotRepository extends JpaRepository<StaffScheduleSlot, Long> {

    List<StaffScheduleSlot> findByStaffRecordIdOrderByDayOfWeekAscStartTimeAsc(Long staffRecordId);

    @Query("""
            SELECT s FROM StaffScheduleSlot s WHERE s.staffRecordId IN :staffRecordIds
            ORDER BY s.dayOfWeek, s.startTime""")
    List<StaffScheduleSlot> findByStaffRecordIds(Collection<Long> staffRecordIds);

    /**
     * Deletes the slots right away, before the new ones are inserted: Hibernate would otherwise run the inserts first,
     * and the new slots would overlap the old ones (ex_staff_schedule_slots_overlap).
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StaffScheduleSlot s WHERE s.staffRecordId = :staffRecordId")
    int deleteByStaffRecordId(Long staffRecordId);

    @Query("""
            SELECT DISTINCT new ec.rehabelite.staff.domain.WorkingDay(r.userId, s.dayOfWeek)
            FROM StaffScheduleSlot s JOIN StaffRecord r ON r.id = s.staffRecordId
            WHERE r.userId IN :userIds""")
    List<WorkingDay> findWorkingDays(Collection<Long> userIds);

}
