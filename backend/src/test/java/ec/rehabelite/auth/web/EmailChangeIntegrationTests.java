package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.RecordingMailSender;
import ec.rehabelite.TestPatients;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-02 end to end: editing the email of a person with an account changes the account's email, which must be
 * confirmed again through a link sent to the new address. Until then the account keeps the previous email and its
 * password; a notice without a link goes to the previous address, and if that notice fails the change goes ahead.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EmailChangeIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave.Activa2026";
    private static final String INVALID_LINK = "El enlace no es válido o ya venció. Solicite uno nuevo.";
    private static final String CONFIRMATION_SUBJECT = "Confirme su nuevo correo de Rehab Elite";
    private static final String NOTICE_SUBJECT = "Se solicitó cambiar el correo de su cuenta de Rehab Elite";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Admin de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
    }

    @Test
    void anActivatedAccountConfirmsTheNewEmailThroughTheLinkAndKeepsItsPassword(CapturedOutput output)
            throws Exception {
        String previous = uniqueEmail("antes");
        String next = uniqueEmail("nuevo");
        Staff staff = activatedStaff(previous);
        String openSession = JsonPath.read(login(previous, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        updateStaffEmail(staff, next.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(next))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.emailChangePending").value(true));

        // Until it is confirmed, the account keeps logging in with the previous email and its open sessions
        User waiting = userRepository.findById(staff.userId()).orElseThrow();
        assertThat(waiting.getEmail()).isEqualTo(previous);
        assertThat(waiting.getPendingEmail()).isEqualTo(next);
        login(previous, PASSWORD).andExpect(status().isOk());
        login(next, PASSWORD).andExpect(status().isUnauthorized());
        sessionCall(openSession).andExpect(status().isOk());

        MimeMessage confirmation = only(mailSender.messagesTo(next));
        assertThat(RecordingMailSender.subjectOf(confirmation)).isEqualTo(CONFIRMATION_SUBJECT);
        String token = RecordingMailSender.tokenOf(confirmation);
        assertThat(RecordingMailSender.plainText(confirmation))
                .contains("http://localhost:4200/confirmar-correo?token=" + token)
                .contains("48 horas")
                .doesNotContain(previous);
        assertThat(RecordingMailSender.html(confirmation)).contains("Confirmar mi nuevo correo");

        // The notice to the previous address has no link and does not name the new address
        MimeMessage notice = noticeTo(previous);
        assertThat(RecordingMailSender.plainText(notice)).doesNotContain("token=").doesNotContain(next)
                .contains("Si usted no esperaba este cambio");
        assertThat(RecordingMailSender.html(notice)).doesNotContain("<a href");

        assertThat(auditEntries("EMAIL_CHANGE", staff.userId())).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", admin.getId()));

        confirmEmail(token).andExpect(status().isNoContent());

        User confirmed = userRepository.findById(staff.userId()).orElseThrow();
        assertThat(confirmed.getEmail()).isEqualTo(next);
        assertThat(confirmed.getPendingEmail()).isNull();
        login(next, PASSWORD).andExpect(status().isOk());
        login(previous, PASSWORD).andExpect(status().isUnauthorized());
        // The sessions opened with the previous email are closed
        sessionCall(openSession).andExpect(status().isUnauthorized());
        assertThat(auditEntries("EMAIL_CONFIRMATION", staff.userId())).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", staff.userId()));
        confirmEmail(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(INVALID_LINK));

        // Neither address is stored in the audit log nor written to the logs
        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE entity_type = 'User' AND entity_id = ?", staff.userId());
        assertThat(entries.toString()).doesNotContain(previous).doesNotContain(next);
        assertThat(output.getAll()).doesNotContain(previous).doesNotContain(next).doesNotContain(token);
    }

    @Test
    void anAccountPendingActivationTakesTheNewEmailAndGetsANewActivationLink() throws Exception {
        String previous = uniqueEmail("pendiente");
        String next = uniqueEmail("corregido");
        Staff staff = registeredStaff(previous);
        String oldLink = RecordingMailSender.tokenOf(only(mailSender.messagesTo(previous)));

        updateStaffEmail(staff, next)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.account.emailChangePending").value(false));

        assertThat(userRepository.findById(staff.userId()).orElseThrow().getEmail()).isEqualTo(next);
        MimeMessage activation = only(mailSender.messagesTo(next));
        assertThat(RecordingMailSender.subjectOf(activation)).isEqualTo("Active su cuenta de Rehab Elite");
        activate(oldLink).andExpect(status().isBadRequest());
        activate(RecordingMailSender.tokenOf(activation)).andExpect(status().isNoContent());
        login(next, PASSWORD).andExpect(status().isOk());
        assertThat(auditEntries("EMAIL_CHANGE", staff.userId())).hasSize(1);
    }

    @Test
    void ifTheNoticeToThePreviousAddressFailsTheChangeGoesAhead() throws Exception {
        String previous = uniqueEmail("aviso-falla");
        String next = uniqueEmail("nuevo");
        Staff staff = activatedStaff(previous);
        mailSender.failFor(previous);
        try {
            updateStaffEmail(staff, next).andExpect(status().isOk());

            assertThat(mailSender.awaitMessagesTo(previous, 2)).hasSize(1);
        } finally {
            mailSender.stopFailingFor(previous);
        }

        assertThat(userRepository.findById(staff.userId()).orElseThrow().getPendingEmail()).isEqualTo(next);
        confirmEmail(RecordingMailSender.tokenOf(only(mailSender.messagesTo(next)))).andExpect(status().isNoContent());
        login(next, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ifTheLinkToTheNewEmailCannotBeSentNothingChanges() throws Exception {
        String previous = uniqueEmail("antes");
        String next = uniqueEmail("sin-enlace");
        Staff staff = activatedStaff(previous);
        mailSender.failFor(next);
        try {
            updateStaffEmail(staff, next)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value(
                            "No se pudo enviar el enlace de confirmación al nuevo correo. Intente nuevamente en unos "
                                    + "minutos."));
        } finally {
            mailSender.stopFailingFor(next);
        }

        assertThat(jdbc.queryForObject("SELECT email FROM staff_records WHERE id = ?", String.class, staff.id()))
                .isEqualTo(previous);
        assertThat(userRepository.findById(staff.userId()).orElseThrow().getPendingEmail()).isNull();
        assertThat(auditEntries("EMAIL_CHANGE", staff.userId())).isEmpty();
        assertThat(mailSender.awaitMessagesTo(previous, 2)).hasSize(1);
    }

    @Test
    void theConfirmationCanBeResentAndGoingBackToTheCurrentEmailCancelsIt() throws Exception {
        String previous = uniqueEmail("antes");
        String next = uniqueEmail("nuevo");
        Staff staff = activatedStaff(previous);
        updateStaffEmail(staff, next).andExpect(status().isOk());
        String first = RecordingMailSender.tokenOf(only(mailSender.messagesTo(next)));

        mockMvc.perform(post("/api/users/{id}/resend-invitation", staff.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailChangePending").value(true));
        List<MimeMessage> confirmations = mailSender.messagesTo(next);
        assertThat(confirmations).hasSize(2);
        String second = RecordingMailSender.tokenOf(confirmations.get(1));
        confirmEmail(first).andExpect(status().isBadRequest());

        updateStaffEmail(staff, previous)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.emailChangePending").value(false));
        confirmEmail(second).andExpect(status().isBadRequest());
        login(previous, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theEmailOfADeactivatedAccountCannotChange() throws Exception {
        String previous = uniqueEmail("inactiva");
        Staff staff = activatedStaff(previous);
        mockMvc.perform(patch("/api/users/{id}/status", staff.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        updateStaffEmail(staff, uniqueEmail("nuevo"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("La cuenta de esta persona está desactivada. Reactívela antes de cambiar el correo."));
        assertThat(jdbc.queryForObject("SELECT email FROM staff_records WHERE id = ?", String.class, staff.id()))
                .isEqualTo(previous);
    }

    @Test
    void thePortalAccountFollowsTheEmailAndTheNamesOfItsPatient() throws Exception {
        String previous = uniqueEmail("paciente");
        String next = uniqueEmail("paciente-nuevo");
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", previous, patientId);
        String body = mockMvc.perform(post("/api/patients/{id}/portal-account", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();
        activate(RecordingMailSender.tokenOf(only(mailSender.messagesTo(previous)))).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"admissionDate":"2026-01-15","firstName":"rosa elena","lastName":"ORTIZ de la vega",
                                 "birthDate":"1985-04-12","sex":"FEMALE","phone":"0990000000","email":"%s",
                                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156",
                                            "mainStreet":"Calle de prueba","crossStreet":"Calle secundaria de prueba",
                                            "reference":"Referencia de prueba"},
                                 "occupation":"Ocupación de prueba","educationLevel":"Superior",
                                 "hasHealthInsurance":false}""".formatted(next)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalAccount.emailChangePending").value(true));

        User account = userRepository.findById(userId).orElseThrow();
        assertThat(account.getFullName()).isEqualTo("Rosa Elena Ortiz de la Vega");
        assertThat(account.getPendingEmail()).isEqualTo(next);
        assertThat(RecordingMailSender.subjectOf(noticeTo(previous))).isEqualTo(NOTICE_SUBJECT);
        confirmEmail(RecordingMailSender.tokenOf(only(mailSender.messagesTo(next)))).andExpect(status().isNoContent());
        login(next, PASSWORD).andExpect(status().isOk());
    }

    private record Staff(Long id, Long userId, String nationalId) {
    }

    private Staff registeredStaff(String email) throws Exception {
        String nationalId = TestPatients.randomNationalId();
        String body = TestStaff.register(mockMvc, adminToken,
                        TestStaff.json("PHYSIOTHERAPIST", nationalId, "Lucía", "Mora Paz", email))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Staff(((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.account.userId")).longValue(), nationalId);
    }

    private Staff activatedStaff(String email) throws Exception {
        Staff staff = registeredStaff(email);
        activate(RecordingMailSender.tokenOf(only(mailSender.messagesTo(email)))).andExpect(status().isNoContent());
        return staff;
    }

    private ResultActions updateStaffEmail(Staff staff, String email) throws Exception {
        return mockMvc.perform(put("/api/staff/{id}", staff.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestStaff.json("PHYSIOTHERAPIST", staff.nationalId(), "Lucía", "Mora Paz", email)));
    }

    private MimeMessage noticeTo(String previous) {
        List<MimeMessage> messages = mailSender.awaitMessagesTo(previous, 2);
        assertThat(messages).hasSize(2);
        MimeMessage notice = messages.get(1);
        assertThat(RecordingMailSender.subjectOf(notice)).isEqualTo(NOTICE_SUBJECT);
        return notice;
    }

    private ResultActions activate(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","password":"%s","passwordConfirmation":"%s"}""".formatted(token, PASSWORD, PASSWORD)));
    }

    private ResultActions confirmEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    /** Any call with an open session of the physiotherapist. */
    private ResultActions sessionCall(String token) throws Exception {
        return mockMvc.perform(get("/api/physio/physiotherapists").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<Map<String, Object>> auditEntries(String action, Long userId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = 'User' AND entity_id = ?""",
                action, userId);
    }

    private static MimeMessage only(List<MimeMessage> messages) {
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
