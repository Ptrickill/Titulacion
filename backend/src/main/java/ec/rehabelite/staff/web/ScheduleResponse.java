package ec.rehabelite.staff.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.staff.ScheduleSlot;
import ec.rehabelite.staff.application.StaffScheduleService.StaffSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * The weekly schedule of a physiotherapist (MEJ-04), with the clinic hours that bound it. The slots come ordered by
 * day and start time, with the contiguous ones merged.
 */
record ScheduleResponse(
        Long staffId,
        String fullName,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "07:00") LocalTime openingTime,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "20:00") LocalTime closingTime,
        List<SlotResponse> slots) {

    record SlotResponse(
            @Schema(example = "MONDAY") DayOfWeek day,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime start,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:00") LocalTime end) {

        static SlotResponse of(ScheduleSlot slot) {
            return new SlotResponse(slot.day(), slot.start(), slot.end());
        }

    }

    static ScheduleResponse of(StaffSchedule schedule, AppProperties.Clinic clinic) {
        return new ScheduleResponse(schedule.record().getId(), schedule.record().fullName(), clinic.openingTime(),
                clinic.closingTime(), schedule.slots().stream().map(SlotResponse::of).toList());
    }

}
