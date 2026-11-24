package ec.rehabelite.staff;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One slot of a physiotherapist's weekly schedule (MEJ-04): a day from Monday to Saturday and the hours, as the
 * half-open range [start, end). Saved schedules never have two overlapping or contiguous slots on the same day: the
 * contiguous ones are merged when the schedule is saved.
 */
public record ScheduleSlot(DayOfWeek day, LocalTime start, LocalTime end) {

    /** Whether the slot covers the whole range of the day, e.g. 08:00-14:00 covers 11:00-13:00. */
    public boolean covers(DayOfWeek day, LocalTime from, LocalTime to) {
        return this.day == day && !start.isAfter(from) && !end.isBefore(to);
    }

}
