package ec.rehabelite.staff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * MEJ-05: the reception's GET /api/people lists only patients with their portal accounts, and the operations
 * director's GET /api/staff only the staff, with the filters by role and by account status (MEJ-02). Every test
 * searches a word unique to its own people, so the shared database never interferes.
 */
@AutoConfigureMockMvc
class PeopleRegistryIntegrationTests extends AbstractIntegrationTest {

    /** Token validation, patient count and slice, and the accounts of the page. */
    private static final long PATIENT_LIST_STATEMENTS = 4;
    /** Token validation, staff count and slice, and the accounts of the page. */
    private static final long STAFF_LIST_STATEMENTS = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private String receptionToken;
    private String directorToken;
    private String word;

    @BeforeEach
    void setUp() throws Exception {
        receptionToken = tokenOf(Role.ADMINISTRATOR, "Recepción de prueba");
        directorToken = tokenOf(Role.OPERATIONS_DIRECTOR, "Dirección de prueba");
        word = randomWord();

        // Staff: pending, activated and deactivated accounts
        registerStaff("PHYSIOTHERAPIST", "Alfa");
        Long active = registerStaff("OPERATIONS_DIRECTOR", "Beta");
        String activeEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, active);
        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"%s","password":"Clave.Activa2026","passwordConfirmation":"Clave.Activa2026"}"""
                        .formatted(RecordingMailSender.tokenOf(mailSender.messagesTo(activeEmail).get(0)))))
                .andExpect(status().isNoContent());
        // The active account has logged in once: the list shows its last access
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave.Activa2026"}""".formatted(activeEmail)))
                .andExpect(status().isOk());
        Long inactive = registerStaff("ADMINISTRATOR", "Gama");
        mockMvc.perform(patch("/api/users/{id}/status", inactive)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        // Patients: without account, with a pending portal account, and one registered before MEJ-03
        TestPatients.insert(jdbc, "Persona", "Delta " + word);
        Long withAccount = TestPatients.insert(jdbc, "Persona", "Epsilon " + word);
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", uniqueEmail("epsilon"), withAccount);
        mockMvc.perform(post("/api/patients/{id}/portal-account", withAccount)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + receptionToken))
                .andExpect(status().isCreated());
        Long legacy = TestPatients.insert(jdbc, "Persona", "Zeta " + word);
        jdbc.update("""
                UPDATE patients SET province_code = NULL, canton_code = NULL, parish_code = NULL, main_street = NULL,
                                    cross_street = NULL WHERE id = ?""", legacy);
    }

    @Test
    void theReceptionListsOnlyThePatientsWithTheStatusOfTheirPortalAccess() throws Exception {
        patients("q", word)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Delta " + word, "Epsilon " + word,
                        "Zeta " + word)))
                .andExpect(jsonPath("$.content[*].type").value(contains("PATIENT", "PATIENT", "PATIENT")))
                .andExpect(jsonPath("$.content[*].accountStatus").value(contains("NONE", "PENDING_ACTIVATION",
                        "NONE")))
                .andExpect(jsonPath("$.content[*].needsCompletion").value(contains(false, false, true)))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[1].userId").exists());

        // The former filters of MEJ-02 no longer reach the staff
        patients("q", word, "type", "STAFF").andExpect(jsonPath("$.totalElements").value(3));
        patients("q", word, "role", "PHYSIOTHERAPIST").andExpect(jsonPath("$.totalElements").value(3));
        patients("q", word, "accountStatus", "NONE")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Delta " + word, "Zeta " + word)));
        patients("q", word, "accountStatus", "PENDING_ACTIVATION")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Epsilon " + word)));
        patients("q", word, "accountStatus", "BLOCKED").andExpect(status().isBadRequest());
    }

    @Test
    void theDirectorListsOnlyTheStaffWithTheFiltersByRoleAndAccountStatus() throws Exception {
        staff("q", word)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Alfa " + capitalized(),
                        "Beta " + capitalized(), "Gama " + capitalized())))
                .andExpect(jsonPath("$.content[*].type").value(contains("STAFF", "STAFF", "STAFF")))
                .andExpect(jsonPath("$.content[*].role").value(contains("PHYSIOTHERAPIST", "OPERATIONS_DIRECTOR",
                        "ADMINISTRATOR")))
                .andExpect(jsonPath("$.content[*].accountStatus").value(contains("PENDING_ACTIVATION", "ACTIVE",
                        "INACTIVE")))
                .andExpect(jsonPath("$.content[1].lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].lastLoginAt").doesNotExist());

        staff("q", word, "role", "PHYSIOTHERAPIST")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Alfa " + capitalized())));
        staff("q", word, "accountStatus", "INACTIVE")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Gama " + capitalized())));
        staff("q", word, "role", "OPERATIONS_DIRECTOR", "accountStatus", "ACTIVE")
                .andExpect(jsonPath("$.totalElements").value(1));
        staff("q", word, "role", "PHYSIOTHERAPIST", "accountStatus", "ACTIVE")
                .andExpect(jsonPath("$.totalElements").value(0));
        // Every staff member has an account, and a patient is not staff
        staff("q", word, "accountStatus", "NONE").andExpect(status().isBadRequest());
        staff("q", word, "role", "PATIENT").andExpect(status().isBadRequest());
    }

    @Test
    void aStaffPageAndAPatientPageAreBuiltFromTheirOwnRegistry() throws Exception {
        staff("q", word, "size", "2", "page", "1")
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Gama " + capitalized())));
        patients("q", word, "size", "2", "page", "1")
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Zeta " + word)));
    }

    @Test
    void searchesByTheBeginningOfTheNationalId() throws Exception {
        String nationalId = jdbc.queryForObject("""
                SELECT national_id FROM staff_records WHERE last_name = ?""", String.class, "Alfa " + capitalized());

        staff("q", nationalId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nationalId").value(nationalId));
        patients("q", nationalId).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aPageNeedsTheSameFixedNumberOfStatementsWhateverItsSize() throws Exception {
        assertThat(statementsOf(() -> patients("q", word, "size", "2"))).isEqualTo(PATIENT_LIST_STATEMENTS);
        assertThat(statementsOf(() -> patients("q", word, "size", "50"))).isEqualTo(PATIENT_LIST_STATEMENTS);
        assertThat(statementsOf(() -> staff("q", word, "size", "2"))).isEqualTo(STAFF_LIST_STATEMENTS);
        assertThat(statementsOf(() -> staff("q", word, "size", "50"))).isEqualTo(STAFF_LIST_STATEMENTS);
        // A filter by status or role reads the accounts that have it, in one more statement
        assertThat(statementsOf(() -> patients("q", word, "accountStatus", "PENDING_ACTIVATION")))
                .isEqualTo(PATIENT_LIST_STATEMENTS + 1);
        assertThat(statementsOf(() -> staff("q", word, "role", "PHYSIOTHERAPIST")))
                .isEqualTo(STAFF_LIST_STATEMENTS + 1);
    }

    @Test
    void theIndicatorsOfEachSideCountTheirOwnAccounts() throws Exception {
        String accounts = "SELECT count(*) FROM users WHERE role = ?";
        String activeOf = "SELECT count(*) FROM users WHERE role = ? AND active AND email_verified_at IS NOT NULL";
        String pendingOf = "SELECT count(*) FROM users WHERE role = ? AND active AND email_verified_at IS NULL";

        mockMvc.perform(get("/api/people/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + receptionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients").value(jdbc.queryForObject("SELECT count(*) FROM patients",
                        Long.class)))
                .andExpect(jsonPath("$.portalActive").value(jdbc.queryForObject(activeOf, Long.class, "PATIENT")))
                .andExpect(jsonPath("$.portalPending").value(jdbc.queryForObject(pendingOf, Long.class, "PATIENT")))
                .andExpect(jsonPath("$.withoutPortal").value(jdbc.queryForObject(
                        "SELECT count(*) FROM patients WHERE portal_user_id IS NULL", Long.class)));

        long activeStaff = jdbc.queryForObject("""
                SELECT count(*) FROM users
                WHERE role <> 'PATIENT' AND active AND email_verified_at IS NOT NULL""", Long.class);
        mockMvc.perform(get("/api/staff/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAccounts").value(activeStaff))
                .andExpect(jsonPath("$.physiotherapists")
                        .value(jdbc.queryForObject(accounts, Long.class, "PHYSIOTHERAPIST")))
                .andExpect(jsonPath("$.receptionists").value(jdbc.queryForObject(accounts, Long.class,
                        "ADMINISTRATOR")))
                .andExpect(jsonPath("$.directors").value(jdbc.queryForObject(accounts, Long.class,
                        "OPERATIONS_DIRECTOR")));

        // Token validation, the patient counts and one GROUP BY; the director only needs the GROUP BY
        assertThat(statementsOf(() -> mockMvc.perform(get("/api/people/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + receptionToken)))).isEqualTo(3);
        assertThat(statementsOf(() -> mockMvc.perform(get("/api/staff/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken)))).isEqualTo(2);
    }

    @Test
    void eachSideIsForbiddenTheOtherList() throws Exception {
        mockMvc.perform(get("/api/staff").header(HttpHeaders.AUTHORIZATION, "Bearer " + receptionToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + receptionToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/people").header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/people/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken))
                .andExpect(status().isForbidden());
        // Both registration forms look up national IDs and emails
        for (String token : new String[] {receptionToken, directorToken}) {
            mockMvc.perform(get("/api/people/matches").param("nationalId", TestPatients.randomNationalId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @FunctionalInterface
    private interface Request {

        ResultActions perform() throws Exception;

    }

    private long statementsOf(Request request) throws Exception {
        request.perform().andExpect(status().isOk());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        request.perform().andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** Registers a staff member whose last name has the word of the test, and returns its account. */
    private Long registerStaff(String role, String surname) throws Exception {
        String body = TestStaff.register(mockMvc, directorToken, TestStaff.json(role,
                        TestPatients.randomNationalId(), "Persona", surname + " " + word,
                        uniqueEmail(surname.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.account.userId")).longValue();
    }

    private ResultActions patients(String... parameters) throws Exception {
        return list("/api/people", receptionToken, parameters);
    }

    private ResultActions staff(String... parameters) throws Exception {
        return list("/api/staff", directorToken, parameters);
    }

    private ResultActions list(String path, String token, String... parameters) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String tokenOf(Role role, String fullName) {
        User user = userRepository.save(User.create(uniqueEmail(role.name().toLowerCase()), "not-a-real-hash",
                fullName, role, null, Instant.now()));
        return tokenService.issue(user, Instant.now()).token();
    }

    /** The staff names are stored with a capital initial (NameCase); TestPatients inserts them as written. */
    private String capitalized() {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static String randomWord() {
        StringBuilder word = new StringBuilder("q");
        for (int i = 0; i < 10; i++) {
            word.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return word.toString();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
