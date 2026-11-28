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
import java.util.Arrays;
import java.util.Base64;
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
        @Valid @NotNull InitialDirector initialDirector,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Ai ai,
        DevSeed devSeed,
        @NotBlank String frontendUrl,
        @Valid @NotNull Mail mail,
        @Valid @DefaultValue Tokens tokens,
        @Valid @DefaultValue Clinic clinic,
        @Valid @NotNull ClinicalData clinicalData,
        @Valid @DefaultValue Session session,
        @Valid @DefaultValue Compliance compliance) {

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

    /**
     * Initial operations director (MEJ-05: INITIAL_DIRECTOR_EMAIL / INITIAL_DIRECTOR_PASSWORD), created at startup when
     * no active operations director exists. Both may be blank when one already exists.
     */
    public record InitialDirector(@Email String email, String password) {

        /** Never print the password, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "InitialDirector[email=" + (email == null || email.isBlank() ? "<blank>" : "<set>") + ", password="
                    + (password == null || password.isBlank() ? "<blank>" : "<set>") + "]";
        }

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

    /**
     * Key of the clinical text encryption (MEJ-06): CLINICAL_DATA_KEY, 32 random bytes in Base64 (AES-256), and the
     * optional CLINICAL_DATA_PREVIOUS_KEYS, the keys used before a rotation, separated by commas, only to read what they
     * encrypted. The application does not start without a valid key. See docs/deployment/cifrado.md.
     */
    public record ClinicalData(
            @NotBlank(message = "CLINICAL_DATA_KEY is required: 32 random bytes in Base64") String key,
            String previousKeys) {

        public static final int KEY_BYTES = 32;

        @AssertTrue(message = "CLINICAL_DATA_KEY must be 32 bytes (AES-256) encoded in Base64")
        public boolean isKeyValid() {
            return key == null || key.isBlank() || isValidKey(key);
        }

        @AssertTrue(message = "Each key of CLINICAL_DATA_PREVIOUS_KEYS must be 32 bytes (AES-256) encoded in Base64")
        public boolean isPreviousKeysValid() {
            return previousKeyList().stream().allMatch(ClinicalData::isValidKey);
        }

        /** The previous keys, without blanks; none when the variable is empty. */
        public List<String> previousKeyList() {
            if (previousKeys == null || previousKeys.isBlank()) {
                return List.of();
            }
            return Arrays.stream(previousKeys.split(",")).map(String::strip).filter(k -> !k.isEmpty())
                    .toList();
        }

        public static boolean isValidKey(String key) {
            try {
                return Base64.getDecoder().decode(key.strip()).length == KEY_BYTES;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /** Never print a key, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "ClinicalData[key=" + (key == null || key.isBlank() ? "<blank>" : "<set>") + ", previousKeys="
                    + previousKeyList().size() + "]";
        }

    }

    /**
     * Session security (MEJ-06): the frontend closes a session after {@code idleTimeout} without activity, warning
     * {@code idleWarning} before. The hard limit is still the expiration of the JWT.
     */
    public record Session(
            @DefaultValue("15m") @NotNull Duration idleTimeout,
            @DefaultValue("2m") @NotNull Duration idleWarning) {

        @AssertTrue(message = "app.session.idle-warning must be shorter than app.session.idle-timeout")
        public boolean isWarningBeforeTimeout() {
            return idleTimeout == null || idleWarning == null
                    || (idleWarning.isPositive() && idleWarning.compareTo(idleTimeout) < 0);
        }

    }

    /**
     * Data protection (MEJ-06): days to answer a request of the data subject, shown as its due date. The legal term
     * is to be confirmed by the clinic (docs/compliance/lopdp.md).
     */
    public record Compliance(@DefaultValue("15") @Positive int requestResponseDays) {
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
