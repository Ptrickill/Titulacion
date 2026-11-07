package ec.rehabelite.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The exceptions Spring MVC raises by itself (wrong method, media type, unreadable body, missing or invalid
 * parameter, unknown path) answer with the same Spanish problem response as the application's own errors, through
 * the whole filter chain, with the title and detail of their status. Their HTTP status does not change, and no
 * detail repeats a value sent by the client.
 */
@AutoConfigureMockMvc
@Import(SpringErrorProblemDetailIntegrationTests.RequiredParameterController.class)
class SpringErrorProblemDetailIntegrationTests extends AbstractIntegrationTest {

    private static final String BAD_REQUEST = "Solicitud no válida";
    private static final String BAD_REQUEST_DETAIL = "La solicitud no tiene un formato válido.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    private User physio;
    private User admin;

    /** No endpoint of the API has a required parameter, so this test-only one raises the exception. */
    @RestController
    static class RequiredParameterController {

        @GetMapping("/api/problem-details-test/required-parameter")
        String required(@RequestParam String q) {
            return q;
        }

    }

    @BeforeEach
    void setUp() {
        physio = createUser(Role.PHYSIOTHERAPIST);
        admin = createUser(Role.ADMINISTRATOR);
    }

    @Test
    void deletingATherapySessionAnswers405InSpanishWithTheAllowedMethods() throws Exception {
        String allowed = perform(physio, delete("/api/therapy-sessions/{id}", 1))
                .andExpect(spanishProblem(405, "Método no permitido",
                        "El método HTTP no está permitido para este recurso.", "/api/therapy-sessions/1"))
                .andReturn().getResponse().getHeader(HttpHeaders.ALLOW);

        // The order of the methods is not fixed
        assertThat(allowed.split(",\\s*")).containsExactlyInAnyOrder("GET", "PUT");
    }

    @Test
    void theAuditLogAnswers405InSpanishToAnyChange() throws Exception {
        perform(admin, post("/api/audit-logs"))
                .andExpect(spanishProblem(405, "Método no permitido",
                        "El método HTTP no está permitido para este recurso.", "/api/audit-logs"))
                .andExpect(jsonPath("$.detail", not(containsString("POST"))));
    }

    @Test
    void anUnsupportedMediaTypeAnswers415InSpanish() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", 1)
                        .contentType(MediaType.TEXT_PLAIN).content("sesión"))
                .andExpect(spanishProblem(415, "Tipo de contenido no soportado",
                        "El tipo de contenido de la solicitud no está soportado.",
                        "/api/treatments/1/therapy-sessions"));
    }

    @Test
    void anUnreadableBodyAnswers400InSpanish() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sessionDate\": 1712345678"))
                .andExpect(spanishProblem(400, BAD_REQUEST, BAD_REQUEST_DETAIL, "/api/treatments/1/therapy-sessions"))
                .andExpect(content().string(not(containsString("1712345678"))));
    }

    @Test
    void aMissingRequiredParameterAnswers400InSpanish() throws Exception {
        perform(physio, get("/api/problem-details-test/required-parameter"))
                .andExpect(spanishProblem(400, BAD_REQUEST, BAD_REQUEST_DETAIL,
                        "/api/problem-details-test/required-parameter"));
    }

    @Test
    void aPathVariableOfTheWrongTypeAnswers400InSpanishWithoutItsValue() throws Exception {
        perform(physio, get("/api/treatments/{id}", "1712345678x"))
                .andExpect(spanishProblem(400, BAD_REQUEST, BAD_REQUEST_DETAIL, "/api/treatments/1712345678x"))
                .andExpect(jsonPath("$.detail", not(containsString("1712345678"))));
    }

    @Test
    void aQueryParameterOfTheWrongTypeAnswers400InSpanishWithoutItsValue() throws Exception {
        perform(admin, get("/api/audit-logs").param("action", "1712345678"))
                .andExpect(spanishProblem(400, BAD_REQUEST, BAD_REQUEST_DETAIL, "/api/audit-logs"))
                .andExpect(content().string(not(containsString("1712345678"))));
    }

    @Test
    void anUnknownPathAnswers404InSpanish() throws Exception {
        perform(physio, get("/api/no-existe"))
                .andExpect(spanishProblem(404, "Recurso no encontrado", "El recurso solicitado no existe.",
                        "/api/no-existe"));
    }

    /** Same shape as the application's own errors: problem+json with Spanish title and detail. */
    private static ResultMatcher spanishProblem(int status, String title, String detail, String instance) {
        List<ResultMatcher> matchers = List.of(
                status().is(status),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(status),
                jsonPath("$.title").value(title),
                jsonPath("$.detail").value(detail),
                jsonPath("$.instance").value(instance));
        return result -> {
            for (ResultMatcher matcher : matchers) {
                matcher.match(result);
            }
        };
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("errores-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
    }

}
