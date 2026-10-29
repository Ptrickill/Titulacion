package ec.rehabelite.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers 401 (missing, invalid or expired token) with {@code WWW-Authenticate: Bearer} and a Spanish
 * problem response. Unauthenticated requests are not audited: there is no user to attribute them to.
 */
@Component
@RequiredArgsConstructor
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String TITLE = "No autenticado";
    static final String DETAIL = "Debe iniciar sesión para acceder a este recurso.";

    private final ProblemResponseWriter writer;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(request, response, HttpStatus.UNAUTHORIZED, TITLE, DETAIL);
    }

}
