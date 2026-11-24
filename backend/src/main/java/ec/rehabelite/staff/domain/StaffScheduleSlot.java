package ec.rehabelite.staff.domain;

import ec.rehabelite.staff.ScheduleSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One slot of the weekly schedule of a physiotherapist's staff record (MEJ-04). The day is the ISO number, 1 (Monday)
 * to 6 (Saturday). A slot is never updated: the whole week is replaced (see {@link WeeklySchedule}).
 */
@Entity
@Table(name = "staff_schedule_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_record_id", nullable = false, updatable = false)
    private Long staffRecordId;

    @Column(name = "day_of_week", nullable = false, updatable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StaffScheduleSlot of(Long staffRecordId, ScheduleSlot slot, Long createdBy, Instant now) {
        StaffScheduleSlot saved = new StaffScheduleSlot();
        saved.staffRecordId = staffRecordId;
        saved.dayOfWeek = (short) slot.day().getValue();
        saved.startTime = slot.start();
        saved.endTime = slot.end();
        saved.createdBy = createdBy;
        saved.createdAt = now;
        return saved;
    }

    public ScheduleSlot toSlot() {
        return new ScheduleSlot(DayOfWeek.of(dayOfWeek), startTime, endTime);
    }

}
