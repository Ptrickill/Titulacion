package ec.rehabelite.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;
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
        @Valid @DefaultValue Tokens tokens,
        @Valid @DefaultValue Clinic clinic) {

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

    /**
     * Account emails (MEJ-01, TEC-16): the sender (MAIL_FROM) and how they leave. {@code transport} is
     * {@link MailTransport#SMTP} by default (MAIL_TRANSPORT empty or missing), with the server under spring.mail, or
     * {@link MailTransport#API} through the Brevo HTTP API, which then needs BREVO_API_KEY.
     */
    public record Mail(
            @NotBlank @Email String from,
            MailTransport transport,
            @Valid @DefaultValue Brevo brevo) {

        public Mail {
            if (transport == null) {
                transport = MailTransport.SMTP;
            }
        }

        @AssertTrue(message = "MAIL_TRANSPORT=api needs BREVO_API_KEY")
        public boolean isApiKeyPresentForApi() {
            return transport != MailTransport.API || (brevo != null && brevo.apiKey() != null
                    && !brevo.apiKey().isBlank());
        }

    }

    /** How the account emails leave (TEC-16). */
    public enum MailTransport {
        /** JavaMailSender and the SMTP server of spring.mail (Mailpit in development). */
        SMTP,
        /** Brevo's transactional email HTTP API: Render's free plan blocks outbound SMTP ports. */
        API
    }

    /**
     * Brevo transactional email API (TEC-16), used only with {@code app.mail.transport=api}. The key comes from
     * BREVO_API_KEY; the base URL and the timeouts have defaults in application.yml.
     */
    public record Brevo(
            String apiKey,
            @DefaultValue("https://api.brevo.com") @NotBlank String baseUrl,
            @DefaultValue("5s") @NotNull Duration connectTimeout,
            @DefaultValue("10s") @NotNull Duration readTimeout) {

        /** Never print the key, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "Brevo[apiKey=" + (apiKey == null || apiKey.isBlank() ? "<blank>" : "<set>") + ", baseUrl="
                    + baseUrl + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
        }

    }

    /**
     * Lifetime of the one-time account links (MEJ-01): 48 hours to activate an account, 1 hour to reset a password.
     */
    public record Tokens(
            @DefaultValue("48h") @NotNull Duration activationTtl,
            @DefaultValue("1h") @NotNull Duration resetTtl) {
    }

    /**
     * Clinic hours (MEJ-04): every slot of a physiotherapist's schedule lies between them. V8 fixes the widest hours,
     * 07:00 to 20:00 (ck_staff_schedule_slots_clinic_hours): these properties may narrow them, never widen them
     * (that needs a new migration), and the application does not start otherwise.
     */
    public record Clinic(
            @DefaultValue("07:00") @DateTimeFormat(pattern = "HH:mm") @NotNull LocalTime openingTime,
            @DefaultValue("20:00") @DateTimeFormat(pattern = "HH:mm") @NotNull LocalTime closingTime) {

        /** The bounds of ck_staff_schedule_slots_clinic_hours (V8). */
        public static final LocalTime EARLIEST_OPENING = LocalTime.of(7, 0);
        public static final LocalTime LATEST_CLOSING = LocalTime.of(20, 0);

        @AssertTrue(message = "app.clinic.opening-time must be before closing-time, both within 07:00 and 20:00")
        public boolean isWithinDatabaseBounds() {
            return openingTime == null || closingTime == null
                    || (!openingTime.isBefore(EARLIEST_OPENING) && !closingTime.isAfter(LATEST_CLOSING)
                            && openingTime.isBefore(closingTime));
        }

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
