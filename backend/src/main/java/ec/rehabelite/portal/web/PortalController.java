package ec.rehabelite.portal.web;

import ec.rehabelite.portal.application.PortalService;
import ec.rehabelite.portal.web.PortalResponses.PortalInstructionResponse;
import ec.rehabelite.portal.web.PortalResponses.PortalSessionResponse;
import ec.rehabelite.portal.web.PortalResponses.PortalTreatmentResponse;
import ec.rehabelite.portal.web.PortalResponses.TreatmentProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient portal (HU-12, HU-13): read only, and always about the patient linked to the authenticated account. No endpoint
 * takes a patient id.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Portal del paciente", description = "Avance, sesiones e indicaciones del propio tratamiento (solo "
        + "paciente, solo lectura)")
class PortalController {

    private final PortalService portalService;
    private final PortalWebMapper mapper;

    @GetMapping(path = "/api/portal/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tratamientos del paciente autenticado",
            description = "El tratamiento activo primero y luego los demás, del más reciente al más antiguo, con su "
                    + "diagnóstico, estado, responsable vigente, sesiones planificadas y sesiones realizadas "
                    + "(COMPLETED). El paciente es el vinculado a la cuenta autenticada; no se recibe ningún id de "
                    + "paciente. Una lista vacía significa que aún no tiene tratamientos. Cada tratamiento listado "
                    + "queda auditado como READ.")
    @ApiResponse(responseCode = "200", description = "Tratamientos del paciente")
    @ApiResponse(responseCode = "403", description = "El rol no es paciente", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un paciente vinculado", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<PortalTreatmentResponse> treatments() {
        return mapper.toResponses(portalService.treatments());
    }

    @GetMapping(path = "/api/portal/treatments/{treatmentId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Avance de un tratamiento del paciente autenticado",
            description = "Sesiones realizadas frente a las planificadas con su porcentaje, y la evolución del dolor: "
                    + "la evaluación inicial seguida de las sesiones COMPLETED, la EVA inicial, la última y la "
                    + "reducción en puntos. Las sesiones RESCHEDULED y NO_SHOW no cuentan ni aparecen en la curva. "
                    + "Cada consulta queda auditada como READ del tratamiento.")
    @ApiResponse(responseCode = "200", description = "Avance del tratamiento")
    @ApiResponse(responseCode = "403", description = "El rol no es paciente, o el tratamiento no es del paciente "
            + "autenticado. Un tratamiento inexistente responde igual, para no revelar qué ids existen. Queda "
            + "auditado como ACCESS_DENIED", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un paciente vinculado", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    TreatmentProgressResponse progress(@PathVariable Long treatmentId) {
        return mapper.toResponse(portalService.progress(treatmentId));
    }

    @GetMapping(path = "/api/portal/treatments/{treatmentId}/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sesiones de un tratamiento del paciente autenticado",
            description = "Todas las sesiones del tratamiento en orden cronológico (fecha, hora y correlativo), sea "
                    + "cual sea su estado, con el fisioterapeuta que las registró, la EVA, las actividades realizadas "
                    + "y las indicaciones para el paciente. Nunca incluye las observaciones clínicas ni la evolución, "
                    + "que son de uso interno. No es paginada. Cada consulta queda auditada como READ del "
                    + "tratamiento.")
    @ApiResponse(responseCode = "200", description = "Sesiones del tratamiento")
    @ApiResponse(responseCode = "403", description = "El rol no es paciente, o el tratamiento no es del paciente "
            + "autenticado. Un tratamiento inexistente responde igual. Queda auditado como ACCESS_DENIED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un paciente vinculado", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<PortalSessionResponse> sessions(@PathVariable Long treatmentId) {
        return mapper.toSessionResponses(portalService.sessions(treatmentId));
    }

    @GetMapping(path = "/api/portal/treatments/{treatmentId}/instructions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Última indicación para el paciente autenticado",
            description = "La indicación para el paciente de la sesión más reciente que tiene una, sea cual sea su "
                    + "estado, con la fecha y el correlativo de esa sesión y el fisioterapeuta que la registró. Si "
                    + "ninguna sesión tiene indicaciones, responde 200 sin cuerpo. Cada consulta queda auditada como "
                    + "READ del tratamiento.")
    @ApiResponse(responseCode = "200", description = "Última indicación; sin cuerpo si no hay ninguna")
    @ApiResponse(responseCode = "403", description = "El rol no es paciente, o el tratamiento no es del paciente "
            + "autenticado. Un tratamiento inexistente responde igual. Queda auditado como ACCESS_DENIED",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un paciente vinculado", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<PortalInstructionResponse> instructions(@PathVariable Long treatmentId) {
        return ResponseEntity.ok(portalService.latestInstruction(treatmentId).map(mapper::toResponse).orElse(null));
    }

}
