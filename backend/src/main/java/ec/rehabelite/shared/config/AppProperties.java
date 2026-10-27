package ec.rehabelite.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-specific settings under {@code app.*}, populated from the root .env file.
 * The application refuses to start if any constraint is violated.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Ai ai) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters long") String secret,
            @Positive long expirationMinutes) {
    }

    /** Initial administrator account; both values may be blank until the admin seeding story is implemented. */
    public record Admin(@Email String email, String password) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /** Anthropic API key; blank until the AI summary sprint. */
    public record Ai(String anthropicApiKey) {
    }

}
