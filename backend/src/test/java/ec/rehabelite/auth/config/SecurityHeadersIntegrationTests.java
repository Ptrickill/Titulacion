package ec.rehabelite.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-06 (B5): every response carries the security headers, also the errors; HSTS only goes over HTTPS (behind the
 * Render proxy), and Swagger UI gets a CSP that lets it load its own scripts and styles.
 */
@AutoConfigureMockMvc
class SecurityHeadersIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anApiResponseCarriesTheHeadersAndAStrictPolicy() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/api/legal/privacy-notice")).andExpect(status().isOk()))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void errorsCarryTheHeadersToo() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/api/patients")).andExpect(status().isUnauthorized()))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void hstsGoesOnlyOverHttps() throws Exception {
        mockMvc.perform(get("/api/legal/privacy-notice").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        // Behind the Render proxy the scheme comes from X-Forwarded-Proto (forward-headers-strategy: framework)
        mockMvc.perform(get("/api/legal/privacy-notice").header("X-Forwarded-Proto", "https"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void swaggerUiMayLoadItsOwnScriptsAndStyles() throws Exception {
        expectCommonHeaders(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'; script-src 'self'; "
                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"));
    }

    private static ResultActions expectCommonHeaders(ResultActions result) throws Exception {
        return result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

}
