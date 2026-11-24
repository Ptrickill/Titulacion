package ec.rehabelite.staff.domain;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.staff.ScheduleSlot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MEJ-04: the rules of a weekly schedule and the merging of contiguous slots (adjustment of D9). */
class WeeklyScheduleTests {

    private static final LocalTime OPENING = LocalTime.of(7, 0);
    private static final LocalTime CLOSING = LocalTime.of(20, 0);

    @Test
    void mergesContiguousSlotsOfTheSameDayAndOrdersTheWeek() {
        List<ScheduleSlot> slots = schedule(
                slot(TUESDAY, "14:00", "18:00"),
                slot(MONDAY, "12:00", "14:00"),
                slot(MONDAY, "08:00", "12:00"),
                slot(MONDAY, "14:00", "15:30"),
                slot(MONDAY, "16:00", "17:00"));

        assertThat(slots).containsExactly(
                slot(MONDAY, "08:00", "15:30"),
                slot(MONDAY, "16:00", "17:00"),
                slot(TUESDAY, "14:00", "18:00"));
    }

    @Test
    void contiguousSlotsOnDifferentDaysStaySeparate() {
        assertThat(schedule(slot(MONDAY, "08:00", "12:00"), slot(TUESDAY, "12:00", "14:00")))
                .containsExactly(slot(MONDAY, "08:00", "12:00"), slot(TUESDAY, "12:00", "14:00"));
    }

    @Test
    void aMergedSlotCoversARangeThatCrossesTheFormerBoundary() {
        List<ScheduleSlot> slots = schedule(slot(FRIDAY, "08:00", "12:00"), slot(FRIDAY, "12:00", "14:00"));

        assertThat(slots).singleElement()
                .satisfies(slot -> assertThat(slot.covers(FRIDAY, LocalTime.of(11, 0), LocalTime.of(13, 0))).isTrue());
    }

    @Test
    void theWholeClinicDayAndAnEmptyWeekAreValid() {
        assertThat(schedule(slot(SATURDAY, "07:00", "20:00"))).containsExactly(slot(SATURDAY, "07:00", "20:00"));
        assertThat(schedule()).isEmpty();
    }

    @Test
    void rejectsOverlappingSlotsNamingTheDayAndBothRanges() {
        assertThatThrownBy(() -> schedule(slot(MONDAY, "08:00", "12:00"), slot(MONDAY, "11:59", "13:00")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Las franjas del lunes se superponen: 08:00–12:00 y 11:59–13:00.");
        // A slot inside another one, and a slot that overlaps a merged one
        assertThatThrownBy(() -> schedule(slot(TUESDAY, "08:00", "18:00"), slot(TUESDAY, "09:00", "10:00")))
                .hasMessage("Las franjas del martes se superponen: 08:00–18:00 y 09:00–10:00.");
        assertThatThrownBy(() -> schedule(slot(TUESDAY, "08:00", "10:00"), slot(TUESDAY, "10:00", "12:00"),
                slot(TUESDAY, "11:00", "13:00")))
                .hasMessage("Las franjas del martes se superponen: 08:00–12:00 y 11:00–13:00.");
    }

    @Test
    void rejectsSlotsOutsideTheClinicHoursOrInverted() {
        assertThatThrownBy(() -> schedule(slot(MONDAY, "06:59", "09:00")))
                .hasMessage("La franja del lunes (06:59–09:00) está fuera de la jornada de la clínica, de 07:00 a 20:00.");
        assertThatThrownBy(() -> schedule(slot(MONDAY, "19:00", "20:01")))
                .hasMessage("La franja del lunes (19:00–20:01) está fuera de la jornada de la clínica, de 07:00 a 20:00.");
        assertThatThrownBy(() -> schedule(slot(MONDAY, "10:00", "10:00")))
                .hasMessage("En la franja del lunes (10:00–10:00), la hora de inicio debe ser anterior a la de fin.");
        // Narrower clinic hours from app.clinic.*
        assertThatThrownBy(() -> WeeklySchedule.of(List.of(slot(MONDAY, "08:00", "18:30")), LocalTime.of(8, 0),
                LocalTime.of(18, 0)))
                .hasMessage("La franja del lunes (08:00–18:30) está fuera de la jornada de la clínica, de 08:00 a 18:00.");
    }

    @Test
    void rejectsSundayAndSeconds() {
        assertThatThrownBy(() -> schedule(slot(SUNDAY, "08:00", "10:00")))
                .hasMessage(WeeklySchedule.SUNDAY);
        assertThatThrownBy(() -> schedule(new ScheduleSlot(MONDAY, LocalTime.of(8, 0, 30), LocalTime.of(9, 0))))
                .hasMessage(WeeklySchedule.WITH_SECONDS);
    }

    private static List<ScheduleSlot> schedule(ScheduleSlot... slots) {
        return WeeklySchedule.of(List.of(slots), OPENING, CLOSING).slots();
    }

    private static ScheduleSlot slot(DayOfWeek day, String start, String end) {
        return new ScheduleSlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

}
