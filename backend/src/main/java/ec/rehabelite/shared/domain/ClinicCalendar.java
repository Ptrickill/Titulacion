package ec.rehabelite.shared.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calendar dates of the clinic (Quito). Instants are stored in UTC, but "today" (admission dates, ages) is the
 * date in Ecuador: at 21:00 in Quito it is already the next day in UTC.
 */
public final class ClinicCalendar {

    public static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    private ClinicCalendar() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

}
