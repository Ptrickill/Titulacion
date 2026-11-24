package ec.rehabelite.staff;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * The weekly schedule of a physiotherapist (MEJ-04) as other modules see it: the staff record, its account and names,
 * and its slots ordered by day and start time. Never the other personal data of the record.
 *
 * @param userId the account: the id that a treatment keeps as its responsible
 */
public record PhysiotherapistSchedule(Long staffId, Long userId, String firstName, String lastName,
        List<ScheduleSlot> slots) {

    /** "First names last names", the account name. */
    public String fullName() {
        return (firstName + " " + lastName).strip();
    }

    /** Whether one slot covers the whole range of the day (contiguous slots are already merged). */
    public boolean covers(DayOfWeek day, LocalTime from, LocalTime to) {
        return slots.stream().anyMatch(slot -> slot.covers(day, from, to));
    }

    public List<ScheduleSlot> slotsOn(DayOfWeek day) {
        return slots.stream().filter(slot -> slot.day() == day).toList();
    }

}
