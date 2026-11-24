package ec.rehabelite.staff.web;

import ec.rehabelite.staff.application.PeopleRegistryService.PersonMatch;
import ec.rehabelite.staff.application.PeopleRegistryService.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;

/** A registered person that already has the national ID or the email (MEJ-02). */
@Schema(description = "Persona registrada que ya tiene la cédula o el correo consultado")
record PersonMatchResponse(
        @Schema(description = "Paciente o personal", example = "PATIENT") PersonType type,
        @Schema(description = "Id del paciente o del registro del personal", example = "15") Long id,
        @Schema(description = "Nombres y apellidos", example = "Juan Carlos Pérez Gómez") String fullName,
        @Schema(description = "Dato que coincide: nationalId o email", example = "email") String field) {

    static PersonMatchResponse of(PersonMatch match) {
        return new PersonMatchResponse(match.type(), match.id(), match.fullName(), match.field());
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PersonMatchResponse[type=" + type + ", id=" + id + "]";
    }

}
