package ec.rehabelite.assignments.domain;

import ec.rehabelite.shared.domain.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The day and range of hours that the operations director asks the availability for (MEJ-04): a day from Monday to
 * Saturday and a range within the clinic hours, the start before the end. A physiotherapist is available when one of
 * its slots covers the whole range.
 */
public record AvailabilityRange(DayOfWeek day, LocalTime from, LocalTime to) {

    public static final String SUNDAY = "La clínica atiende de lunes a sábado: elija otro día.";
    public static final String INVERTED = "La hora inicial debe ser anterior a la hora final.";

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * @throws InvalidRequestException if the day is Sunday, the range is empty or inverted, or it leaves the clinic
     *         hours
     */
    public static AvailabilityRange of(DayOfWeek day, LocalTime from, LocalTime to, LocalTime openingTime,
            LocalTime closingTime) {
        if (day == DayOfWeek.SUNDAY) {
            throw new InvalidRequestException(SUNDAY);
        }
        if (!from.isBefore(to)) {
            throw new InvalidRequestException(INVERTED);
        }
        if (from.isBefore(openingTime) || to.isAfter(closingTime)) {
            throw new InvalidRequestException("El rango debe estar dentro de la jornada de la clínica, de %s a %s."
                    .formatted(HOUR.format(openingTime), HOUR.format(closingTime)));
        }
        return new AvailabilityRange(day, from, to);
    }

}
