package ec.rehabelite.staff.domain;

import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.staff.ScheduleSlot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The rules of a physiotherapist's weekly schedule (MEJ-04), checked before saving it, with messages in Spanish:
 * <ul>
 * <li>days from Monday to Saturday;</li>
 * <li>hours and minutes, without seconds;</li>
 * <li>each slot starts before it ends and lies within the clinic hours ({@code app.clinic.*});</li>
 * <li>two slots of the same day never overlap.</li>
 * </ul>
 * Contiguous slots of the same day are merged (08:00-12:00 and 12:00-14:00 become 08:00-14:00), so one slot covers
 * every range of working time without a gap. The database repeats these rules (V8) as a safety net.
 */
public final class WeeklySchedule {

    public static final String SUNDAY = "La clínica atiende de lunes a sábado: el domingo no puede tener franjas.";
    public static final String WITH_SECONDS = "Las horas de las franjas van en horas y minutos, sin segundos.";

    private static final Locale SPANISH = Locale.forLanguageTag("es-EC");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final Comparator<ScheduleSlot> BY_DAY_AND_TIME = Comparator.comparing(ScheduleSlot::day)
            .thenComparing(ScheduleSlot::start)
            .thenComparing(ScheduleSlot::end);

    private final List<ScheduleSlot> slots;

    private WeeklySchedule(List<ScheduleSlot> slots) {
        this.slots = List.copyOf(slots);
    }

    /**
     * Checks the requested slots, in any order, and returns them ordered by day and start time, with the contiguous
     * ones merged.
     *
     * @throws InvalidRequestException with the first rule that a slot breaks
     */
    public static WeeklySchedule of(List<ScheduleSlot> requested, LocalTime openingTime, LocalTime closingTime) {
        for (ScheduleSlot slot : requested) {
            check(slot, openingTime, closingTime);
        }
        List<ScheduleSlot> merged = new ArrayList<>();
        for (ScheduleSlot slot : requested.stream().sorted(BY_DAY_AND_TIME).toList()) {
            ScheduleSlot previous = merged.isEmpty() ? null : merged.getLast();
            if (previous == null || previous.day() != slot.day() || previous.end().isBefore(slot.start())) {
                merged.add(slot);
            } else if (previous.end().equals(slot.start())) {
                merged.set(merged.size() - 1, new ScheduleSlot(slot.day(), previous.start(), slot.end()));
            } else {
                throw new InvalidRequestException("Las franjas del %s se superponen: %s y %s."
                        .formatted(dayName(slot.day()), range(previous), range(slot)));
            }
        }
        return new WeeklySchedule(merged);
    }

    public List<ScheduleSlot> slots() {
        return slots;
    }

    /** "lunes", "martes", ... */
    public static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, SPANISH);
    }

    private static void check(ScheduleSlot slot, LocalTime openingTime, LocalTime closingTime) {
        if (slot.day() == DayOfWeek.SUNDAY) {
            throw new InvalidRequestException(SUNDAY);
        }
        if (!isWholeMinute(slot.start()) || !isWholeMinute(slot.end())) {
            throw new InvalidRequestException(WITH_SECONDS);
        }
        if (!slot.start().isBefore(slot.end())) {
            throw new InvalidRequestException("En la franja del %s (%s), la hora de inicio debe ser anterior a la de fin."
                    .formatted(dayName(slot.day()), range(slot)));
        }
        if (slot.start().isBefore(openingTime) || slot.end().isAfter(closingTime)) {
            throw new InvalidRequestException(
                    "La franja del %s (%s) está fuera de la jornada de la clínica, de %s a %s."
                            .formatted(dayName(slot.day()), range(slot), HOUR.format(openingTime),
                                    HOUR.format(closingTime)));
        }
    }

    private static boolean isWholeMinute(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    private static String range(ScheduleSlot slot) {
        return HOUR.format(slot.start()) + "–" + HOUR.format(slot.end());
    }

}
