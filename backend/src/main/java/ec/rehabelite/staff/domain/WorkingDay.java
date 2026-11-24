package ec.rehabelite.staff.domain;

/** A day of the week (ISO number) with at least one slot of the account (MEJ-04, the director's treatment list). */
public record WorkingDay(Long userId, short dayOfWeek) {
}
