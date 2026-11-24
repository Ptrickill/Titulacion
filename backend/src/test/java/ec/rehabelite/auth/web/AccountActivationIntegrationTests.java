package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.RecordingMailSender;
import ec.rehabelite.TestStaff;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-01 end to end: account activation through the emailed link, resent invitations, and the password reset
 * ("¿Olvidó su contraseña?") with its request limit. Emails are captured by {@link RecordingMailSender}.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountActivationIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave.Activa2026";
    private static final String NEW_PASSWORD = "Clave.Nueva2026";
    private static final String INVALID_LINK = "El enlace no es válido o ya venció. Solicite uno nuevo.";
    private static final String RESET_ANSWER = "Si el correo corresponde a una cuenta activa, recibirá en unos minutos "
            + "un enlace para restablecer la contraseña.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Admin de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
    }

    @Test
    void theEmailedLinkActivatesTheAccountOnceAndThenItCanLogIn(CapturedOutput output) throws Exception {
        String email = uniqueEmail("fisio");
        Long userId = createAccount(email);
        String token = onlyActivationToken(email);

        setPassword("/api/auth/activate", token, PASSWORD, PASSWORD).andExpect(status().isNoContent());

        User activated = userRepository.findById(userId).orElseThrow();
        assertThat(activated.getEmailVerifiedAt()).isNotNull();
        assertThat(activated.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, activated.getPasswordHash())).isTrue();
        assertThat(activated.getUpdatedBy()).isNull();
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM account_tokens WHERE user_id = ?",
                Boolean.class, userId)).isTrue();
        assertThat(auditEntries("ACTIVATION", userId)).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", userId));
        listUsers().andExpect(jsonPath("$[?(@.id == %d)].status".formatted(userId)).value("ACTIVE"));
        login(email, PASSWORD).andExpect(status().isOk());

        // Single use
        setPassword("/api/auth/activate", token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
        login(email, PASSWORD).andExpect(status().isOk());
        // Neither the token nor the password reach the logs
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(PASSWORD);
    }

    @Test
    void anExpiredActivationLinkIsRejected() throws Exception {
        String email = uniqueEmail("vencido");
        Long userId = createAccount(email);
        String token = onlyActivationToken(email);
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", userId);

        setPassword("/api/auth/activate", token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));

        assertThat(userRepository.findById(userId).orElseThrow().isPendingActivation()).isTrue();
        assertThat(auditEntries("ACTIVATION", userId)).isEmpty();
    }

    @Test
    void anUnknownTokenGetsTheSameAnswer() throws Exception {
        setPassword("/api/auth/activate", "x".repeat(43), PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
    }

    @Test
    void theNewPasswordMustFollowTheRulesBeforeTheLinkIsSpent() throws Exception {
        String email = uniqueEmail("reglas");
        Long userId = createAccount(email);
        String token = onlyActivationToken(email);

        setPassword("/api/auth/activate", token, "SoloLetrasLargas", "SoloLetrasLargas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("debe contener al menos una letra y un número"));
        setPassword("/api/auth/activate", token, "corta1", "corta1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        setPassword("/api/auth/activate", token, PASSWORD, "Otra.Clave2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La confirmación no coincide con la contraseña."));
        String tooManyBytes = "ñ".repeat(37) + "1"; // 75 bytes in UTF-8 but only 38 characters
        setPassword("/api/auth/activate", token, tooManyBytes, tooManyBytes)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña no puede superar los 72 bytes."));

        // None of them spent the link
        setPassword("/api/auth/activate", token, PASSWORD, PASSWORD).andExpect(status().isNoContent());
        assertThat(userRepository.findById(userId).orElseThrow().isPendingActivation()).isFalse();
    }

    @Test
    void resendingTheInvitationReplacesThePreviousLink() throws Exception {
        String email = uniqueEmail("reenvio");
        Long userId = createAccount(email);
        String first = onlyActivationToken(email);

        mockMvc.perform(post("/api/users/{id}/resend-invitation", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));

        List<MimeMessage> messages = mailSender.messagesTo(email);
        assertThat(messages).hasSize(2);
        String second = RecordingMailSender.tokenOf(messages.get(1));
        assertThat(second).isNotEqualTo(first);
        assertThat(auditEntries("INVITATION_RESENT", userId)).hasSize(1);

        setPassword("/api/auth/activate", first, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
        setPassword("/api/auth/activate", second, PASSWORD, PASSWORD).andExpect(status().isNoContent());

        // Once activated, there is nothing to resend
        mockMvc.perform(post("/api/users/{id}/resend-invitation", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Solo se puede reenviar el enlace a una cuenta habilitada que aún no se ha activado o que "
                                + "tiene un cambio de correo por confirmar."));
        mockMvc.perform(post("/api/users/{id}/resend-invitation", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDeactivatedPendingAccountCannotBeActivated() throws Exception {
        String email = uniqueEmail("desactivada");
        Long userId = createAccount(email);
        String token = onlyActivationToken(email);
        mockMvc.perform(patch("/api/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        setPassword("/api/auth/activate", token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
    }

    @Test
    void thePasswordResetSendsAOneHourLinkThatChangesThePasswordAndClosesOpenSessions(CapturedOutput output)
            throws Exception {
        String email = uniqueEmail("olvido");
        User user = userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Olvido Clave",
                Role.ADMINISTRATOR, null, Instant.now()));
        String openSession = tokenService.issue(user, Instant.now()).token();
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + openSession))
                .andExpect(status().isOk());

        forgotPassword(email.toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(RESET_ANSWER));

        MimeMessage message = onlyMessage(mailSender.awaitMessagesTo(email, 1));
        assertThat(RecordingMailSender.subjectOf(message)).isEqualTo("Restablezca su contraseña de Rehab Elite");
        String token = RecordingMailSender.tokenOf(message);
        assertThat(RecordingMailSender.plainText(message))
                .contains("http://localhost:4200/restablecer-contrasena?token=" + token)
                .contains("1 hora");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM account_tokens
                WHERE user_id = ? AND purpose = 'PASSWORD_RESET' AND expires_at = created_at + interval '1 hour'""",
                Integer.class, user.getId())).isEqualTo(1);
        assertThat(auditEntries("RESET_REQUEST", user.getId())).singleElement()
                .satisfies(entry -> assertThat(entry.get("user_id")).isNull());

        // A reset link is not an activation link
        setPassword("/api/auth/activate", token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());

        setPassword("/api/auth/reset-password", token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + openSession))
                .andExpect(status().isUnauthorized());
        assertThat(auditEntries("PASSWORD_RESET", user.getId())).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", user.getId()));
        setPassword("/api/auth/reset-password", token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK));
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void theResetClearsTheLockAndTheFailedAttempts() throws Exception {
        String email = uniqueEmail("bloqueo");
        User user = userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Cuenta Bloqueada",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            login(email, "Clave.Incorrecta1").andExpect(status().isUnauthorized());
        }

        forgotPassword(email).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMessage(mailSender.awaitMessagesTo(email, 1)));
        setPassword("/api/auth/reset-password", token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        User reset = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reset.getFailedAttempts()).isZero();
        assertThat(reset.getLockedUntil()).isNull();
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        String email = uniqueEmail("nadie");
        int unknownRequestsBefore = unknownResetRequests();

        forgotPassword(email)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(RESET_ANSWER));

        assertThat(mailSender.awaitMessagesTo(email, 1)).isEmpty();
        assertThat(unknownResetRequests()).isEqualTo(unknownRequestsBefore + 1);
    }

    @Test
    void pendingOrDeactivatedAccountsGetNoResetLink() throws Exception {
        String pending = uniqueEmail("pendiente");
        createAccount(pending);
        String deactivated = uniqueEmail("inactiva");
        User inactive = userRepository.save(User.create(deactivated, passwordEncoder.encode(PASSWORD), "Inactiva",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        inactive.deactivate(null, Instant.now());
        userRepository.save(inactive);

        forgotPassword(pending).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(RESET_ANSWER));
        forgotPassword(deactivated).andExpect(status().isAccepted());

        assertThat(mailSender.awaitMessagesTo(deactivated, 1)).isEmpty();
        // Only the activation email
        assertThat(mailSender.messagesTo(pending)).singleElement()
                .satisfies(m -> assertThat(RecordingMailSender.subjectOf(m)).isEqualTo("Active su cuenta de Rehab Elite"));
    }

    @Test
    void anAccountGetsAtMostThreeResetLinksPerHourAndOnlyTheLastOneWorks() throws Exception {
        String email = uniqueEmail("limite");
        User user = userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Limite Solicitudes",
                Role.OPERATIONS_DIRECTOR, null, Instant.now()));

        for (int request = 0; request < 4; request++) {
            forgotPassword(email)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(RESET_ANSWER));
            if (request < 3) {
                assertThat(mailSender.awaitMessagesTo(email, request + 1)).hasSize(request + 1);
            }
        }

        assertThat(mailSender.awaitMessagesTo(email, 4)).hasSize(3);
        assertThat(auditEntries("RESET_REQUEST", user.getId())).hasSize(4);
        List<MimeMessage> messages = mailSender.messagesTo(email);
        String first = RecordingMailSender.tokenOf(messages.get(0));
        String last = RecordingMailSender.tokenOf(messages.get(2));
        setPassword("/api/auth/reset-password", first, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest());
        setPassword("/api/auth/reset-password", last, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // Requests older than an hour no longer count
        jdbc.update("UPDATE account_tokens SET created_at = created_at - interval '61 minutes' WHERE user_id = ?",
                user.getId());
        forgotPassword(email).andExpect(status().isAccepted());
        assertThat(mailSender.awaitMessagesTo(email, 4)).hasSize(4);
    }

    @Test
    void auditEntriesNeverCarryTheTokenNorThePassword() throws Exception {
        String email = uniqueEmail("auditoria");
        Long userId = createAccount(email);
        String token = onlyActivationToken(email);
        setPassword("/api/auth/activate", token, PASSWORD, PASSWORD).andExpect(status().isNoContent());

        // audit_logs only has metadata columns: none of them can hold the token or the password
        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE entity_type = 'User' AND entity_id = ?", userId);
        assertThat(entries).extracting(e -> e.get("action"))
                .containsExactlyInAnyOrder("CREATE", "INVITATION_SENT", "ACTIVATION");
        assertThat(entries.toString()).doesNotContain(token).doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM account_tokens WHERE user_id = ?", String.class, userId))
                .hasSize(64).doesNotContain(token);
    }

    /** Since MEJ-02 an account is created by registering its person. */
    private Long createAccount(String email) throws Exception {
        return TestStaff.registerAccount(mockMvc, adminToken, "PHYSIOTHERAPIST", email);
    }

    private String onlyActivationToken(String email) {
        return RecordingMailSender.tokenOf(onlyMessage(mailSender.messagesTo(email)));
    }

    private static MimeMessage onlyMessage(List<MimeMessage> messages) {
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    private ResultActions setPassword(String path, String token, String password, String confirmation)
            throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","password":"%s","passwordConfirmation":"%s"}""".formatted(token, password, confirmation)));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions listUsers() throws Exception {
        return mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private List<Map<String, Object>> auditEntries(String action, Long userId) {
        return jdbc.queryForList("""
                SELECT user_id, ip_address FROM audit_logs
                WHERE action = ? AND entity_type = 'User' AND entity_id = ?""", action, userId);
    }

    private int unknownResetRequests() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action = 'RESET_REQUEST' AND entity_type = 'User' AND entity_id IS NULL AND user_id IS NULL""",
                Integer.class);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
