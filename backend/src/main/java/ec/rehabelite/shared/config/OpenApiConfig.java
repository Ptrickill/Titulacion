package ec.rehabelite.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at /v3/api-docs (Swagger UI at /swagger-ui.html). Every operation requires the
 * JWT bearer token unless it declares an empty security requirement (e.g. login).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI rehabEliteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rehab Elite API")
                        .version("v1")
                        .description("API de gestión clínica y seguimiento terapéutico de Rehab Elite."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtenido en POST /api/auth/login.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

}
