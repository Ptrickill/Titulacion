package ec.rehabelite.staff.web;

import ec.rehabelite.staff.ScheduleSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Body of PUT /api/staff/{id}/schedule (MEJ-04): the whole week, which replaces the saved one. An empty list leaves
 * the physiotherapist without a schedule.
 */
record ScheduleRequest(
        @NotNull @Size(max = 42) List<@Valid @NotNull SlotRequest> slots) {

    /** A day from MONDAY to SATURDAY and the hours as HH:mm. */
    record SlotRequest(
            @NotNull @Schema(example = "MONDAY") DayOfWeek day,
            @NotNull @Schema(type = "string", example = "08:00") LocalTime start,
            @NotNull @Schema(type = "string", example = "12:00") LocalTime end) {
    }

    List<ScheduleSlot> toSlots() {
        return slots.stream().map(slot -> new ScheduleSlot(slot.day(), slot.start(), slot.end())).toList();
    }

}
