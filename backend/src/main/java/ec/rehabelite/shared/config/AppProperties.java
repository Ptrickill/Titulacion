package ec.rehabelite.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
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
        @Valid @NotNull Ai ai,
        DevSeed devSeed,
        @NotBlank String frontendUrl,
        @Valid @NotNull Mail mail,
        @Valid @DefaultValue Tokens tokens) {

    /**
     * Base URL of the Angular application (APP_FRONTEND_URL), without the trailing slash: the emailed links
     * (MEJ-01) point to its public pages.
     */
    public String frontendBaseUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

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

    /** Sender of the account emails (MAIL_FROM); the SMTP server is configured under spring.mail. */
    public record Mail(@NotBlank @Email String from) {
    }

    /**
     * Lifetime of the one-time account links (MEJ-01): 48 hours to activate an account, 1 hour to reset a password.
     */
    public record Tokens(
            @DefaultValue("48h") @NotNull Duration activationTtl,
            @DefaultValue("1h") @NotNull Duration resetTtl) {
    }

    /** Synthetic development data (dev profile only, otherwise null): password of the seed accounts. */
    public record DevSeed(String password) {

        /** Never print the password, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "DevSeed[password=" + (password == null || password.isBlank() ? "<blank>" : "<set>") + "]";
        }

    }

}
