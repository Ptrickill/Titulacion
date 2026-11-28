package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.DataSubjectRequestService.ErasureRequest;
import ec.rehabelite.patients.application.DataSubjectRequestService.TrackedRequest;
import ec.rehabelite.patients.domain.DataRequestStatus;
import ec.rehabelite.patients.domain.DataRequestType;
import ec.rehabelite.patients.domain.DataSubjectRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** Requests and responses of the requests of the data subject (MEJ-06). */
final class DataRequestResponses {

    private DataRequestResponses() {
    }

    @Schema(description = "Solicitud del titular recibida en recepción")
    record RegisterDataRequest(
            @Schema(description = "Derecho que ejerce", example = "ACCESS") @NotNull DataRequestType type,
            @Schema(description = "Fecha de recepción (hoy o antes)", example = "2026-11-20")
            @NotNull @PastOrPresent LocalDate receivedOn) {
    }

    @Schema(description = "Nuevo estado de la solicitud")
    record ChangeDataRequestStatus(
            @Schema(description = "IN_PROGRESS, RESOLVED o REJECTED", example = "RESOLVED")
            @NotNull DataRequestStatus status,
            @Schema(description = "Respuesta al titular, obligatoria para cerrarla (hasta 2000 caracteres)")
            @Size(max = DataSubjectRequest.MAX_RESPONSE_LENGTH) String response) {
    }

    @Schema(description = "Aceptación de una solicitud de eliminación por la dirección operativa")
    record AcceptErasure(
            @Schema(description = "Cédula del paciente, escrita para confirmar", example = "1710034065")
            @NotBlank @Pattern(regexp = "\\d{10}", message = "Ingrese los 10 dígitos de la cédula del paciente.")
            String nationalId,
            @Schema(description = "Respuesta al titular (hasta 2000 caracteres)")
            @NotBlank @Size(max = DataSubjectRequest.MAX_RESPONSE_LENGTH) String response) {

        /** Never print the national ID, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "AcceptErasure[...]";
        }

    }

    @Schema(description = "Rechazo de una solicitud de eliminación por la dirección operativa")
    record RejectErasure(
            @Schema(description = "Respuesta al titular (hasta 2000 caracteres)")
            @NotBlank @Size(max = DataSubjectRequest.MAX_RESPONSE_LENGTH) String response) {
    }

    @Schema(description = "Solicitud del titular con su plazo")
    record DataRequestResponse(
            @Schema(example = "4") Long id,
            @Schema(description = "Derecho que ejerce", example = "ACCESS") DataRequestType type,
            @Schema(description = "Fecha de recepción", example = "2026-11-20") LocalDate receivedOn,
            @Schema(description = "Estado", example = "RECEIVED") DataRequestStatus status,
            @Schema(description = "Respuesta al titular; null mientras está abierta") String response,
            @Schema(description = "Fecha y hora en que se cerró; null mientras está abierta") Instant resolvedAt,
            @Schema(description = "Fecha límite de respuesta: la recepción más app.compliance.request-response-days "
                    + "(plazo a verificar por la clínica)", example = "2026-12-05") LocalDate dueOn,
            @Schema(description = "Días desde la recepción, en Quito", example = "6") long daysElapsed,
            @Schema(description = "Abierta y con la fecha límite vencida", example = "false") boolean overdue) {

        static DataRequestResponse of(TrackedRequest tracked) {
            DataSubjectRequest request = tracked.request();
            return new DataRequestResponse(request.getId(), request.getType(), request.getReceivedOn(),
                    request.getStatus(), request.getResponse(), request.getResolvedAt(), tracked.dueOn(),
                    tracked.daysElapsed(), tracked.overdue());
        }

    }

    @Schema(description = "Solicitud de eliminación abierta, para la dirección operativa")
    record ErasureRequestResponse(
            @Schema(description = "La solicitud con su plazo") DataRequestResponse request,
            @Schema(example = "15") Long patientId,
            @Schema(description = "Nombres y apellidos del paciente", example = "Lucía Ortiz Vega") String patientName,
            @Schema(description = "Cédula del paciente, que se escribe para confirmar", example = "1710034065")
            String nationalId) {

        static ErasureRequestResponse of(ErasureRequest erasure) {
            return new ErasureRequestResponse(DataRequestResponse.of(erasure.tracked()),
                    erasure.tracked().request().getPatientId(), erasure.patientName(), erasure.nationalId());
        }

        /** Never print personal data, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "ErasureRequestResponse[patientId=" + patientId + "]";
        }

    }

}
