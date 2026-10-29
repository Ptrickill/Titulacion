package ec.rehabelite.patients.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/patients/portal-candidates: patients that can still receive a portal account (HU-02).
 */
@AutoConfigureMockMvc
class PatientControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    /** Unique surname per test run, so other tests' patients never match the queries. */
    private String surname;

    @BeforeEach
    void setUp() {
        surname = "Ñúñez" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void findsPatientsWithoutPortalAccountIgnoringAccentsAndCase() throws Exception {
        Long free = TestPatients.insert(jdbc, "José Andrés", surname);
        Long linked = TestPatients.insert(jdbc, "María", surname);
        Long portalUser = userRepository.save(User.create("portal-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "María", Role.PATIENT, null, Instant.now())).getId();
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", portalUser, linked);

        // No accents and different case than the stored "Ñúñez…" / "José"
        String query = "nunez" + surname.substring(5).toUpperCase() + " jose";
        candidates(query, Role.ADMINISTRATOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(free.intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(linked.intValue()))))
                .andExpect(jsonPath("$[0].fullName").value(surname + " José Andrés"))
                .andExpect(jsonPath("$[0].medicalRecordNumber").value("HC-" + TestPatients.nationalIdOf(jdbc, free)));

        candidates(surname.toLowerCase(), Role.ADMINISTRATOR)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findsPatientsByNationalIdPrefix() throws Exception {
        Long patient = TestPatients.insert(jdbc, "Elena", surname);
        String nationalId = TestPatients.nationalIdOf(jdbc, patient);

        candidates(nationalId, Role.ADMINISTRATOR)
                .andExpect(jsonPath("$[*].id").value(hasItem(patient.intValue())))
                .andExpect(jsonPath("$[*].nationalId").value(everyItem(startsWith(nationalId))));
    }

    @Test
    void likeWildcardsAreNotInterpreted() throws Exception {
        TestPatients.insert(jdbc, "Elena", surname);

        candidates("%" + surname + "_", Role.ADMINISTRATOR)
                .andExpect(jsonPath("$.length()").value(1));
        candidates("%%%", Role.ADMINISTRATOR).andExpect(status().isOk());
    }

    @Test
    void onlyTheAdministratorCanListCandidates() throws Exception {
        candidates("", Role.PHYSIOTHERAPIST).andExpect(status().isForbidden());
        candidates("", Role.PATIENT).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/portal-candidates")).andExpect(status().isUnauthorized());
    }

    private ResultActions candidates(String query, Role role) throws Exception {
        User user = userRepository.save(User.create("rol-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
        String token = tokenService.issue(user, Instant.now()).token();
        return mockMvc.perform(get("/api/patients/portal-candidates").param("query", query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

}
