package ec.rehabelite.shared.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void notFoundReturnsSpanishProblemDetail() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));
    }

    @Test
    void businessRuleReturns422() throws Exception {
        mockMvc.perform(get("/business-rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
    }

    @Test
    void validationErrorListsFields() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void unreadableBodyIsTranslated() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"));
    }

    @Test
    void unexpectedErrorHidesDetailsAndReturnsErrorId() throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
    }

    @Test
    void accessDeniedIsLeftToTheSecurityFilterChain() {
        // Propagates out of MVC (instead of becoming a 500) so the AccessDeniedHandler answers 403 and audits it
        assertThatThrownBy(() -> mockMvc.perform(get("/access-denied")))
                .rootCause()
                .isInstanceOf(AccessDeniedException.class);
    }

    record SampleRequest(@NotBlank String name) {
    }

    @RestController
    static class SampleController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("El paciente no existe.");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("El tratamiento ya fue finalizado.");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal detail that must not leak");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("method security denial");
        }

    }

}
