package ec.rehabelite.assignments.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import ec.rehabelite.assignments.application.AssignmentService.PhysiotherapistLoad;
import ec.rehabelite.assignments.application.AssignmentService.TreatmentAssignment;
import ec.rehabelite.staff.ScheduleSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Responses of /api/operations (MEJ-04). None of them carries clinical content: no diagnosis, plan, notes nor
 * sessions, and of the patient only the name (LOPDP, the minimum needed to assign a physiotherapist).
 */
final class OperationsResponses {

    private OperationsResponses() {
    }

    record SlotResponse(
            @Schema(example = "MONDAY") DayOfWeek day,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime start,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:00") LocalTime end) {

        static List<SlotResponse> of(List<ScheduleSlot> slots) {
            return slots.stream().map(slot -> new SlotResponse(slot.day(), slot.start(), slot.end())).toList();
        }

    }

    /** The weekly board: the clinic hours and every physiotherapist, active or not, by last names. */
    record ScheduleBoardResponse(
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "07:00") LocalTime openingTime,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "20:00") LocalTime closingTime,
            List<BoardPhysiotherapistResponse> physiotherapists) {
    }

    /**
     * A physiotherapist of the board.
     *
     * @param physiotherapistId its account, the id a treatment keeps as its responsible
     * @param activeTreatments its load: the ACTIVE treatments it is the current responsible of
     */
    record BoardPhysiotherapistResponse(Long staffId, Long physiotherapistId, String fullName, boolean active,
            long activeTreatments, List<SlotResponse> slots) {

        static BoardPhysiotherapistResponse of(PhysiotherapistLoad load) {
            return new BoardPhysiotherapistResponse(load.schedule().staffId(), load.schedule().userId(),
                    load.schedule().fullName(), load.active(), load.activeTreatments(),
                    SlotResponse.of(load.schedule().slots()));
        }

    }

    /** An available physiotherapist, with its load and its slots of the requested day. */
    record AvailablePhysiotherapistResponse(Long physiotherapistId, Long staffId, String fullName,
            long activeTreatments, List<SlotResponse> slots) {

        static AvailablePhysiotherapistResponse of(PhysiotherapistLoad load, DayOfWeek day) {
            return new AvailablePhysiotherapistResponse(load.schedule().userId(), load.schedule().staffId(),
                    load.schedule().fullName(), load.activeTreatments(), SlotResponse.of(load.schedule().slotsOn(day)));
        }

    }

    record ResponsibleResponse(Long id, String fullName) {
    }

    /**
     * An ACTIVE treatment of the director's list.
     *
     * @param responsibleInactive the current responsible has a deactivated account: the treatment needs a new one
     * @param scheduleDays the days of the current responsible's schedule, Monday first
     */
    record OperationsTreatmentResponse(Long id, String patientName, LocalDate startDate,
            ResponsibleResponse responsible, boolean responsibleInactive, List<DayOfWeek> scheduleDays) {

        static OperationsTreatmentResponse of(TreatmentAssignment assignment) {
            Long responsibleId = assignment.treatment().responsibleId();
            return new OperationsTreatmentResponse(assignment.treatment().id(), assignment.patientName(),
                    assignment.treatment().startDate(),
                    responsibleId == null ? null : new ResponsibleResponse(responsibleId, assignment.responsibleName()),
                    assignment.responsibleInactive(), assignment.scheduleDays().stream().sorted().toList());
        }

    }

}
