package ec.rehabelite.staff.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.staff.application.PeopleRegistryService.PersonEntry;
import ec.rehabelite.staff.application.PeopleRegistryService.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A row of the people registry (MEJ-02): identification, contact and the status of the account. */
@Schema(description = "Persona del Registro de Usuarios")
record PersonResponse(
        @Schema(description = "Paciente o personal", example = "STAFF") PersonType type,
        @Schema(description = "Id del paciente o del registro del personal", example = "4") Long id,
        @Schema(description = "Cédula; null si el registro aún no la tiene", example = "1710034065") String nationalId,
        @Schema(description = "Nombres", example = "Lucía Fernanda") String firstName,
        @Schema(description = "Apellidos", example = "Mora Paz") String lastName,
        @Schema(description = "Rol: PATIENT para los pacientes, el de la cuenta para el personal",
                example = "PHYSIOTHERAPIST") Role role,
        @Schema(description = "Correo electrónico; null si se borró por una solicitud de eliminación",
                example = "lucia.mora@correo.ec") String email,
        @Schema(description = "Teléfono; null si falta", example = "0987654321") String phone,
        @Schema(description = "Estado de la cuenta: NONE (sin cuenta), PENDING_ACTIVATION, ACTIVE o INACTIVE",
                example = "ACTIVE") String accountStatus,
        @Schema(description = "Id de la cuenta; null sin cuenta", example = "12") Long userId,
        @Schema(description = "Tiene un cambio de correo esperando confirmación", example = "false")
        boolean emailChangePending,
        @Schema(description = "Datos por completar", example = "false") boolean needsCompletion,
        @Schema(description = "Último inicio de sesión (UTC); null sin cuenta o si nunca ingresó")
        Instant lastLoginAt,
        @Schema(description = "Paciente bloqueado por una solicitud de eliminación aceptada (MEJ-06)", example = "false")
        boolean blocked) {

    static PersonResponse of(PersonEntry entry) {
        return new PersonResponse(entry.type(), entry.id(), entry.nationalId(), entry.firstName(), entry.lastName(),
                entry.role(), entry.email(), entry.phone(),
                entry.account() == null ? "NONE" : entry.account().status().name(),
                entry.account() == null ? null : entry.account().userId(),
                entry.account() != null && entry.account().emailChangePending(), entry.needsCompletion(),
                entry.account() == null ? null : entry.account().lastLoginAt(), entry.blocked());
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PersonResponse[type=" + type + ", id=" + id + "]";
    }

}
