package ec.rehabelite.staff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestStaff;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
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
 * MEJ-02: the national ID is unique between patients and staff; the email of a staff member is unique against
 * patients, staff and accounts (also a pending new email); patients may share an email, but then only one of them can
 * get a portal account with it. Checked through the public interfaces of the modules, in both directions.
 */
@AutoConfigureMockMvc
class PersonIdentityUniquenessIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    /** MEJ-05: the reception registers the patients and the operations director the staff. */
    private String adminToken;
    private String directorToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Recepción de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
        User director = userRepository.save(User.create(uniqueEmail("director"), "not-a-real-hash",
                "Dirección de prueba", Role.OPERATIONS_DIRECTOR, null, Instant.now()));
        directorToken = tokenService.issue(director, Instant.now()).token();
    }

    @Test
    void aStaffMemberCannotTakeTheNationalIdOfAPatientAndViceVersa() throws Exception {
        String patientNationalId = TestPatients.randomNationalId();
        Long patientId = registerPatient(patientNationalId, uniqueEmail("paciente"));

        TestStaff.register(mockMvc, directorToken,
                        TestStaff.json("PHYSIOTHERAPIST", patientNationalId, "Ana", "Paz", uniqueEmail("fisio")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta cédula pertenece a un paciente registrado."))
                .andExpect(jsonPath("$.existingPatientId").value(patientId))
                .andExpect(jsonPath("$.field").value("nationalId"));

        String staffNationalId = TestPatients.randomNationalId();
        Long staffId = registerStaff(staffNationalId, uniqueEmail("fisio"));
        patientRequest(post("/api/patients"), patientJson(staffNationalId, uniqueEmail("paciente")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta cédula pertenece a un miembro del personal registrado."))
                .andExpect(jsonPath("$.existingStaffId").value(staffId))
                .andExpect(jsonPath("$.field").value("nationalId"));

        TestStaff.register(mockMvc, directorToken,
                        TestStaff.json("ADMINISTRATOR", staffNationalId, "Otra", "Persona", uniqueEmail("admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingStaffId").value(staffId));
    }

    @Test
    void theEmailOfAStaffMemberIsUniqueAgainstPatientsStaffAndAccounts() throws Exception {
        String patientEmail = uniqueEmail("paciente");
        Long patientId = registerPatient(TestPatients.randomNationalId(), patientEmail);
        TestStaff.register(mockMvc, directorToken, TestStaff.json("PHYSIOTHERAPIST", TestPatients.randomNationalId(),
                        "Ana", "Paz", patientEmail.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este correo pertenece a un paciente registrado."))
                .andExpect(jsonPath("$.existingPatientId").value(patientId))
                .andExpect(jsonPath("$.field").value("email"));

        String staffEmail = uniqueEmail("fisio");
        Long staffId = registerStaff(TestPatients.randomNationalId(), staffEmail);
        TestStaff.register(mockMvc, directorToken, TestStaff.json("PHYSIOTHERAPIST", TestPatients.randomNationalId(),
                        "Otra", "Persona", staffEmail))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingStaffId").value(staffId));
        // A patient cannot take a staff member's email either
        patientRequest(post("/api/patients"), patientJson(TestPatients.randomNationalId(), staffEmail))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este correo pertenece a un miembro del personal registrado."))
                .andExpect(jsonPath("$.existingStaffId").value(staffId));

        // A new email still waiting for confirmation also counts
        String pending = uniqueEmail("pendiente");
        jdbc.update("UPDATE users SET pending_email = ? WHERE id = (SELECT user_id FROM staff_records WHERE id = ?)",
                pending, staffId);
        TestStaff.register(mockMvc, directorToken, TestStaff.json("PHYSIOTHERAPIST", TestPatients.randomNationalId(),
                        "Otra", "Persona", pending))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese correo electrónico."))
                .andExpect(jsonPath("$.existingStaffId").value(staffId));
        patientRequest(post("/api/patients"), patientJson(TestPatients.randomNationalId(), pending))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese correo electrónico."));
    }

    @Test
    void twoPatientsWithTheSameEmailAreSavedButOnlyOneGetsThePortalAccess() throws Exception {
        String familyEmail = uniqueEmail("familia");
        Long mother = registerPatient(TestPatients.randomNationalId(), familyEmail);
        Long son = registerPatient(TestPatients.randomNationalId(), familyEmail.toUpperCase());

        assertThat(jdbc.queryForList("SELECT email FROM patients WHERE id IN (?, ?)", String.class, mother, son))
                .containsOnly(familyEmail);
        mockMvc.perform(get("/api/people/matches").param("email", familyEmail)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(mother.intValue(), son.intValue())))
                .andExpect(jsonPath("$[*].type").value(containsInAnyOrder("PATIENT", "PATIENT")));

        portalAccount(mother).andExpect(status().isCreated());
        portalAccount(son)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con el correo de este paciente. Corrija "
                        + "el correo del paciente antes de crear su acceso al portal."));
        assertThat(jdbc.queryForObject("SELECT portal_user_id FROM patients WHERE id = ?", Long.class, son)).isNull();

        // Editing the second patient with the shared email still works: it only has to differ from staff emails
        patientRequest(put("/api/patients/{id}", son), patientJson(null, familyEmail)).andExpect(status().isOk());
    }

    @Test
    void theLookupFindsTheNationalIdAndTheEmailInBothRegistries() throws Exception {
        String nationalId = TestPatients.randomNationalId();
        String email = uniqueEmail("fisio");
        Long staffId = registerStaff(nationalId, email);

        mockMvc.perform(get("/api/people/matches").param("nationalId", nationalId).param("email", email.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].field").value(containsInAnyOrder("nationalId", "email")))
                .andExpect(jsonPath("$[0].type").value("STAFF"))
                .andExpect(jsonPath("$[0].id").value(staffId))
                .andExpect(jsonPath("$[0].fullName").value("Ana Paz"));
        mockMvc.perform(get("/api/people/matches").param("nationalId", TestPatients.randomNationalId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Long registerStaff(String nationalId, String email) throws Exception {
        String body = TestStaff.register(mockMvc, directorToken,
                        TestStaff.json("PHYSIOTHERAPIST", nationalId, "Ana", "Paz", email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long registerPatient(String nationalId, String email) throws Exception {
        String body = patientRequest(post("/api/patients"), patientJson(nationalId, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions portalAccount(Long patientId) throws Exception {
        return mockMvc.perform(post("/api/patients/{id}/portal-account", patientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private ResultActions patientRequest(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Body of POST /api/patients, or of PUT without national ID when it is null. */
    private static String patientJson(String nationalId, String email) {
        String identification = nationalId == null ? "" : "\"nationalId\":\"%s\",".formatted(nationalId);
        return """
                {%s"admissionDate":"2026-01-15","firstName":"Rosa Elena","lastName":"Ortiz Vega",
                 "birthDate":"1968-04-12","sex":"FEMALE","phone":"0990000101","email":"%s",
                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Av. Ilaló",
                            "crossStreet":"Río Pita","reference":"Frente al parque"},
                 "occupation":"Contadora","educationLevel":"Superior","hasHealthInsurance":true}"""
                .formatted(identification, email);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
