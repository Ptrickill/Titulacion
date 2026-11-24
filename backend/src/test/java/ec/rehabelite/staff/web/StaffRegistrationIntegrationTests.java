package ec.rehabelite.staff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ec.rehabelite.shared.config.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-02 end to end: the administrator registers a staff member with the same personal data as a patient, and the
 * account is created with that email and those names in the same operation, followed by the MEJ-01 invitation.
 */
@AutoConfigureMockMvc
class StaffRegistrationIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private AppProperties properties;

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
    void registersTheStaffMemberAndItsAccountInOneOperationAndEmailsTheInvitation() throws Exception {
        String email = uniqueEmail("fisio");
        String nationalId = TestPatients.randomNationalId();

        String body = TestStaff.register(mockMvc, adminToken,
                        TestStaff.json("PHYSIOTHERAPIST", nationalId, "  LUCÍA   fernanda ", "mora  DE LA paz",
                                email.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nationalId").value(nationalId))
                .andExpect(jsonPath("$.firstName").value("Lucía Fernanda"))
                .andExpect(jsonPath("$.lastName").value("Mora de la Paz"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PHYSIOTHERAPIST"))
                .andExpect(jsonPath("$.account.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.account.emailChangePending").value(false))
                .andExpect(jsonPath("$.address.formatted").value(
                        "Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. "
                                + "Referencia: Frente al parque"))
                .andExpect(jsonPath("$.needsCompletion").value(false))
                .andReturn().getResponse().getContentAsString();
        Long staffId = ((Number) JsonPath.read(body, "$.id")).longValue();
        Long userId = ((Number) JsonPath.read(body, "$.account.userId")).longValue();

        // The account name is derived from the record, never typed
        User account = userRepository.findById(userId).orElseThrow();
        assertThat(account.getFullName()).isEqualTo("Lucía Fernanda Mora de la Paz");
        assertThat(account.getEmail()).isEqualTo(email);
        assertThat(account.getRole()).isEqualTo(Role.PHYSIOTHERAPIST);
        assertThat(account.getPasswordHash()).isNull();
        assertThat(jdbc.queryForObject("SELECT user_id FROM staff_records WHERE id = ?", Long.class, staffId))
                .isEqualTo(userId);

        assertThat(auditEntries("CREATE", "StaffRecord", staffId)).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("user_id", admin.getId()));
        assertThat(auditEntries("CREATE", "User", userId)).hasSize(1);
        assertThat(auditEntries("INVITATION_SENT", "User", userId)).hasSize(1);
        assertThat(mailSender.messagesTo(email)).singleElement().satisfies(message -> {
            assertThat(RecordingMailSender.subjectOf(message)).isEqualTo("Active su cuenta de Rehab Elite");
            assertThat(RecordingMailSender.plainText(message))
                    .contains("Lucía Fernanda Mora de la Paz")
                    .contains("/activar-cuenta?token=" + RecordingMailSender.tokenOf(message));
        });
    }

    @Test
    void aMailFailureAnswers503AndCreatesNeitherTheRecordNorTheAccount() throws Exception {
        String email = uniqueEmail("sin-correo");
        String nationalId = TestPatients.randomNationalId();
        mailSender.failFor(email);
        try {
            TestStaff.register(mockMvc, adminToken, TestStaff.json("OPERATIONS_DIRECTOR", nationalId, "Sin", "Correo",
                            email))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail")
                            .value("No se pudo enviar el correo de activación. Intente nuevamente en unos minutos."));
        } finally {
            mailSender.stopFailingFor(email);
        }

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_records WHERE national_id = ?", Integer.class,
                nationalId)).isZero();
    }

    @Test
    void thePatientRoleAndInvalidDataAreRejected() throws Exception {
        TestStaff.register(mockMvc, adminToken, TestStaff.json("PATIENT", TestPatients.randomNationalId(), "Rosa",
                        "Ortiz", uniqueEmail("paciente")))
                .andExpect(status().isBadRequest());

        TestStaff.register(mockMvc, adminToken, """
                        {"role":"PHYSIOTHERAPIST","nationalId":"1234567890","firstName":"","lastName":"X",
                         "birthDate":"1990-01-01","sex":"MALE","phone":"12345","email":"no-es-correo",
                         "address":{"provinceCode":"09","cantonCode":"1701","parishCode":"170156","mainStreet":"A",
                                    "crossStreet":"B","reference":"C"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("nationalId", "firstName", "phone",
                        "email", "address.parishCode")));
    }

    @Test
    void editingTheNamesRenamesTheAccountAndTheSameDataChangesNothing() throws Exception {
        String email = uniqueEmail("renombre");
        String nationalId = TestPatients.randomNationalId();
        Long staffId = register(nationalId, "Ana", "Paz", email);
        Long userId = userIdOf(staffId);

        update(staffId, TestStaff.json("PHYSIOTHERAPIST", nationalId, "ana lucía", "PAZ  ruiz", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana Lucía"))
                .andExpect(jsonPath("$.lastName").value("Paz Ruiz"));

        assertThat(userRepository.findById(userId).orElseThrow().getFullName()).isEqualTo("Ana Lucía Paz Ruiz");
        assertThat(auditEntries("UPDATE", "StaffRecord", staffId)).hasSize(1);
        // The same data again: no change and no audit
        update(staffId, TestStaff.json("PHYSIOTHERAPIST", nationalId, "Ana Lucía", "Paz Ruiz", email))
                .andExpect(status().isOk());
        assertThat(auditEntries("UPDATE", "StaffRecord", staffId)).hasSize(1);
        assertThat(mailSender.messagesTo(email)).hasSize(1);
    }

    @Test
    void theRegisteredNationalIdNeverChanges() throws Exception {
        String email = uniqueEmail("cedula");
        String nationalId = TestPatients.randomNationalId();
        Long staffId = register(nationalId, "Ana", "Paz", email);

        update(staffId, TestStaff.json("PHYSIOTHERAPIST", TestPatients.randomNationalId(), "Ana", "Paz", email))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La cédula registrada no se puede modificar."));
    }

    @Test
    void aRecordOfAnAccountThatExistedBeforeShowsDataToCompleteAndIsCompletedWhenEdited() throws Exception {
        String email = uniqueEmail("anterior");
        User existing = userRepository.save(User.create(email, "not-a-real-hash", "Paula Herrera Vásconez",
                Role.OPERATIONS_DIRECTOR, null, Instant.now()));
        // What V7 does with an existing staff account
        Long staffId = jdbc.queryForObject("""
                INSERT INTO staff_records (user_id, first_name, last_name, email)
                VALUES (?, 'Paula', 'Herrera Vásconez', ?) RETURNING id""", Long.class, existing.getId(), email);

        mockMvc.perform(get("/api/staff/{id}", staffId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").doesNotExist())
                .andExpect(jsonPath("$.age").doesNotExist())
                .andExpect(jsonPath("$.address.complete").value(false))
                .andExpect(jsonPath("$.role").value("OPERATIONS_DIRECTOR"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.needsCompletion").value(true));

        // Every field is required when editing it
        update(staffId, "{\"firstName\":\"Paula\",\"lastName\":\"Herrera Vásconez\",\"email\":\"" + email + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("nationalId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address")));

        String nationalId = TestPatients.randomNationalId();
        update(staffId, TestStaff.json("OPERATIONS_DIRECTOR", nationalId, "Paula", "Herrera Vásconez", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").value(nationalId))
                .andExpect(jsonPath("$.needsCompletion").value(false));
    }

    @Test
    void theInitialAdministratorOfANewDatabaseHasItsRecord() {
        // The test database starts empty, so app.admin is created at startup, after V7 (StaffAccountCreated)
        Map<String, Object> record = jdbc.queryForMap("""
                SELECT s.first_name, s.last_name, s.email, s.national_id, u.full_name
                FROM staff_records s JOIN users u ON u.id = s.user_id
                WHERE u.email = ?""", properties.admin().email());

        assertThat(record).containsEntry("first_name", "Administrador")
                .containsEntry("last_name", "del sistema")
                .containsEntry("email", properties.admin().email())
                .containsEntry("full_name", "Administrador del sistema");
        assertThat(record.get("national_id")).isNull();
    }

    @Test
    void anUnknownRecordIsNotFoundAndOnlyTheAdministratorRegistersStaff() throws Exception {
        mockMvc.perform(get("/api/staff/{id}", 999_999_999L).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El registro del personal no existe."));

        User physio = userRepository.save(User.create(uniqueEmail("fisio"), "not-a-real-hash", "Fisio",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        mockMvc.perform(post("/api/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(physio, Instant.now()).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestStaff.json("PHYSIOTHERAPIST", TestPatients.randomNationalId(), "A", "B",
                                uniqueEmail("x"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theEndpointsAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/staff'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/staff/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/people'].get").exists())
                .andExpect(jsonPath("$.paths['/api/people/matches'].get").exists())
                .andExpect(jsonPath("$.paths['/api/patients/{id}/portal-account'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/confirm-email'].post").exists());
    }

    private Long register(String nationalId, String firstName, String lastName, String email) throws Exception {
        String body = TestStaff.register(mockMvc, adminToken,
                        TestStaff.json("PHYSIOTHERAPIST", nationalId, firstName, lastName, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions update(Long staffId, String body) throws Exception {
        return mockMvc.perform(put("/api/staff/{id}", staffId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long userIdOf(Long staffId) {
        return jdbc.queryForObject("SELECT user_id FROM staff_records WHERE id = ?", Long.class, staffId);
    }

    private List<Map<String, Object>> auditEntries(String action, String entityType, Long entityId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ?""",
                action, entityType, entityId);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
