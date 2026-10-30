package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.ClinicCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * HU-05 end to end: the administrator registers patients through POST /api/patients, and GET /api/patients/{id}
 * gives access to an existing record.
 */
@AutoConfigureMockMvc
class PatientRegistrationIntegrationTests extends AbstractIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1996, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private String adminToken;
    private String nationalId;

    @BeforeEach
    void setUp() {
        admin = createUser(Role.ADMINISTRATOR);
        adminToken = tokenOf(admin);
        nationalId = TestPatients.randomNationalId();
    }

    @Test
    void registersThePatientWithTheNationalIdAsMedicalRecordNumberAndAuditsIt() throws Exception {
        LocalDate today = ClinicCalendar.today(Clock.systemUTC());
        int expectedAge = Period.between(BIRTH_DATE, today).getYears();

        String location = register(adminToken, body(nationalId, Map.of(
                        "ethnicSelfIdentification", "Mestizo", "religion", "Católica")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nationalId").value(nationalId))
                .andExpect(jsonPath("$.medicalRecordNumber").value(nationalId))
                .andExpect(jsonPath("$.admissionDate").value(today.toString()))
                .andExpect(jsonPath("$.firstName").value("Juan Carlos"))
                .andExpect(jsonPath("$.lastName").value("Pérez Gómez"))
                .andExpect(jsonPath("$.age").value(expectedAge))
                .andExpect(jsonPath("$.sex").value("MALE"))
                .andExpect(jsonPath("$.email").value("juan.perez@correo.ec"))
                .andExpect(jsonPath("$.ethnicSelfIdentification").value("Mestizo"))
                .andExpect(jsonPath("$.religion").value("Católica"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        Long id = patientIdOf(nationalId);
        assertThat(location).isEqualTo("/api/patients/" + id);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT medical_record_number, created_by, created_at, admission_date FROM patients WHERE id = ?""", id);
        assertThat(row).containsEntry("medical_record_number", nationalId)
                .containsEntry("created_by", admin.getId());
        assertThat(row.get("created_at")).isNotNull();
        assertThat(auditAuthors("CREATE", id)).containsExactly(admin.getId());
    }

    @Test
    void ethnicSelfIdentificationAndReligionAreOptional() throws Exception {
        register(adminToken, body(nationalId, Map.of("ethnicSelfIdentification", " ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ethnicSelfIdentification").doesNotExist())
                .andExpect(jsonPath("$.religion").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT ethnic_self_identification, religion FROM patients WHERE id = ?", patientIdOf(nationalId));
        assertThat(row.get("ethnic_self_identification")).isNull();
        assertThat(row.get("religion")).isNull();
    }

    @Test
    void theClientCannotChooseTheMedicalRecordNumber() throws Exception {
        register(adminToken, body(nationalId, Map.of("medicalRecordNumber", "HC-999", "admissionDate", "2026-01-05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medicalRecordNumber").value(nationalId))
                .andExpect(jsonPath("$.admissionDate").value("2026-01-05"));
    }

    @Test
    void invalidNationalIdIsRejected() throws Exception {
        String wrongCheckDigit = nationalId.substring(0, 9) + (Character.getNumericValue(nationalId.charAt(9)) + 1) % 10;

        register(adminToken, body(wrongCheckDigit, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors[0].field").value("nationalId"))
                .andExpect(jsonPath("$.errors[0].message").value("no es una cédula ecuatoriana válida"));

        assertThat(patientCount(wrongCheckDigit)).isZero();
    }

    @Test
    void existingNationalIdAnswers409WithTheExistingPatient() throws Exception {
        Long existing = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        String takenNationalId = TestPatients.nationalIdOf(jdbc, existing);

        register(adminToken, body(takenNationalId, Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail").value("Ya existe un paciente registrado con esta cédula."))
                .andExpect(jsonPath("$.existingPatientId").value(existing));

        assertThat(patientCount(takenNationalId)).isEqualTo(1);
        assertThat(auditAuthors("CREATE", existing)).isEmpty();
    }

    @Test
    void datesAreChecked() throws Exception {
        LocalDate today = ClinicCalendar.today(Clock.systemUTC());

        register(adminToken, body(nationalId, Map.of("birthDate", today.plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
        register(adminToken, body(nationalId, Map.of("admissionDate", today.plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("admissionDate"));
        register(adminToken, body(nationalId, Map.of("admissionDate", "1990-01-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "La fecha de nacimiento no puede ser posterior a la fecha de ingreso."));

        assertThat(patientCount(nationalId)).isZero();
    }

    @Test
    void requiredPersonalDataIsValidated() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalId\":\"%s\"}".formatted(nationalId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(10));
    }

    @Test
    void onlyTheAdministratorRegistersPatients() throws Exception {
        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role);
            register(tokenOf(user), body(nationalId, Map.of()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción."));
            assertThat(jdbc.queryForList("""
                    SELECT entity_type FROM audit_logs WHERE user_id = ? AND action = 'ACCESS_DENIED'""",
                    String.class, user.getId())).containsExactly("patients");
        }
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body(nationalId, Map.of())))
                .andExpect(status().isUnauthorized());

        assertThat(patientCount(nationalId)).isZero();
    }

    @Test
    void administratorAndPhysiotherapistLookUpARegisteredPatient() throws Exception {
        register(adminToken, body(nationalId, Map.of())).andExpect(status().isCreated());
        Long id = patientIdOf(nationalId);

        for (Role role : List.of(Role.ADMINISTRATOR, Role.PHYSIOTHERAPIST)) {
            find(tokenOf(createUser(role)), id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.medicalRecordNumber").value(nationalId))
                    .andExpect(jsonPath("$.age").isNumber());
        }
        find(tokenOf(createUser(Role.PATIENT)), id).andExpect(status().isForbidden());
        find(adminToken, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));
    }

    @Test
    void openApiDocumentsTheRegistration() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/patients'].post.tags[0]").value("Pacientes"))
                .andExpect(jsonPath("$.paths['/api/patients'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/patients'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/patients/{id}'].get.responses['404']").exists());
    }

    /** A valid registration; {@code overrides} replace or add fields (strings only). */
    private static String body(String nationalId, Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("nationalId", nationalId),
                Map.entry("firstName", "Juan Carlos"),
                Map.entry("lastName", "Pérez Gómez"),
                Map.entry("birthDate", BIRTH_DATE.toString()),
                Map.entry("sex", "MALE"),
                Map.entry("phone", "+593 98 765 4321"),
                Map.entry("email", "Juan.Perez@correo.ec"),
                Map.entry("address", "Av. Ilaló y Río Pita, Valle de los Chillos"),
                Map.entry("occupation", "Ingeniero civil"),
                Map.entry("educationLevel", "Superior"),
                Map.entry("healthInsurance", "IESS")));
        fields.putAll(overrides);
        return fields.entrySet().stream()
                .map(e -> "\"%s\":\"%s\"".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private ResultActions register(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/patients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions find(String token, Long id) throws Exception {
        return mockMvc.perform(get("/api/patients/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long patientIdOf(String nationalId) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE national_id = ?", Long.class, nationalId);
    }

    private Integer patientCount(String nationalId) {
        return jdbc.queryForObject("SELECT count(*) FROM patients WHERE national_id = ?", Integer.class, nationalId);
    }

    private List<Long> auditAuthors(String action, Long patientId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = 'Patient' AND entity_id = ?
                ORDER BY id""", Long.class, action, patientId);
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("paciente-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
