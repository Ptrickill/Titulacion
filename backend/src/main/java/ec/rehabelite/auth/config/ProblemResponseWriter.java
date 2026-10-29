package ec.rehabelite.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes RFC 9457 problem responses from the security filter chain, which runs before Spring MVC and
 * therefore cannot use the global exception handler. The JSON has the same shape as MVC's ProblemDetail.
 */
@Component
@RequiredArgsConstructor
class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
            String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

}
