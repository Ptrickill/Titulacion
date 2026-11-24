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
 * MEJ-02: GET /api/people lists patients and staff in one registry, staff first, with the status of each account and
 * the filters by type and role. Every test searches a word unique to its own people, so the shared database never
 * interferes.
 */
@AutoConfigureMockMvc
class PeopleRegistryIntegrationTests extends AbstractIntegrationTest {

    /** Token validation, staff count and slice, patient count and slice, and the accounts of the page. */
    private static final long LIST_STATEMENTS = 6;

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

    private String adminToken;
    private String word;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Admin de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
        word = randomWord();

        // Staff: pending, activated and deactivated accounts
        registerStaff("PHYSIOTHERAPIST", "Alfa");
        Long active = registerStaff("OPERATIONS_DIRECTOR", "Beta");
        String activeEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, active);
        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"%s","password":"Clave.Activa2026","passwordConfirmation":"Clave.Activa2026"}"""
                        .formatted(RecordingMailSender.tokenOf(mailSender.messagesTo(activeEmail).get(0)))))
                .andExpect(status().isNoContent());
        // The active account has logged in once: the registry shows its last access
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Clave.Activa2026"}""".formatted(activeEmail)))
                .andExpect(status().isOk());
        Long inactive = registerStaff("ADMINISTRATOR", "Gama");
        mockMvc.perform(patch("/api/users/{id}/status", inactive)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        // Patients: without account, with a pending portal account, and one registered before MEJ-03
        TestPatients.insert(jdbc, "Persona", "Delta " + word);
        Long withAccount = TestPatients.insert(jdbc, "Persona", "Epsilon " + word);
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", uniqueEmail("epsilon"), withAccount);
        mockMvc.perform(post("/api/patients/{id}/portal-account", withAccount)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated());
        Long legacy = TestPatients.insert(jdbc, "Persona", "Zeta " + word);
        jdbc.update("""
                UPDATE patients SET province_code = NULL, canton_code = NULL, parish_code = NULL, main_street = NULL,
                                    cross_street = NULL WHERE id = ?""", legacy);
    }

    @Test
    void listsTheStaffFirstAndThenThePatientsWithTheStatusOfEachAccount() throws Exception {
        list("q", word)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Alfa " + capitalized(),
                        "Beta " + capitalized(), "Gama " + capitalized(), "Delta " + word, "Epsilon " + word,
                        "Zeta " + word)))
                .andExpect(jsonPath("$.content[*].type").value(contains("STAFF", "STAFF", "STAFF", "PATIENT",
                        "PATIENT", "PATIENT")))
                .andExpect(jsonPath("$.content[*].role").value(contains("PHYSIOTHERAPIST", "OPERATIONS_DIRECTOR",
                        "ADMINISTRATOR", "PATIENT", "PATIENT", "PATIENT")))
                .andExpect(jsonPath("$.content[*].accountStatus").value(contains("PENDING_ACTIVATION", "ACTIVE",
                        "INACTIVE", "NONE", "PENDING_ACTIVATION", "NONE")))
                .andExpect(jsonPath("$.content[*].needsCompletion").value(contains(false, false, false, false, false,
                        true)))
                .andExpect(jsonPath("$.content[3].userId").doesNotExist())
                .andExpect(jsonPath("$.content[4].userId").exists());
    }

    @Test
    void filtersByTypeAndByRole() throws Exception {
        list("q", word, "type", "STAFF")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].type").value(contains("STAFF", "STAFF", "STAFF")));
        list("q", word, "type", "PATIENT")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Delta " + word, "Epsilon " + word,
                        "Zeta " + word)));
        list("q", word, "role", "PHYSIOTHERAPIST")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Alfa " + capitalized()));
        list("q", word, "type", "STAFF", "role", "OPERATIONS_DIRECTOR")
                .andExpect(jsonPath("$.content[*].accountStatus").value(contains("ACTIVE")));

        list("q", word, "type", "PATIENT", "role", "ADMINISTRATOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El filtro de rol solo aplica al personal."));
        list("role", "PATIENT").andExpect(status().isBadRequest());
    }

    @Test
    void aPageCanStartAmongTheStaffAndEndAmongThePatients() throws Exception {
        list("q", word, "size", "2", "page", "0")
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Alfa " + capitalized(),
                        "Beta " + capitalized())));
        list("q", word, "size", "2", "page", "1")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Gama " + capitalized(), "Delta " + word)));
        list("q", word, "size", "2", "page", "2")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Epsilon " + word, "Zeta " + word)));
        list("q", word, "size", "2", "page", "3")
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void searchesByTheBeginningOfTheNationalId() throws Exception {
        String nationalId = jdbc.queryForObject("""
                SELECT national_id FROM staff_records WHERE last_name = ?""", String.class, "Alfa " + capitalized());

        list("q", nationalId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nationalId").value(nationalId));
    }

    @Test
    void aPageNeedsTheSameFixedNumberOfStatementsWhateverItsSize() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        list("q", word, "size", "4", "page", "0").andExpect(status().isOk());

        statistics.clear();
        list("q", word, "size", "4", "page", "0").andExpect(status().isOk());
        long smallPage = statistics.getPrepareStatementCount();
        statistics.clear();
        list("q", word, "size", "50", "page", "0").andExpect(status().isOk());
        long largePage = statistics.getPrepareStatementCount();

        assertThat(smallPage).isEqualTo(LIST_STATEMENTS);
        assertThat(largePage).isEqualTo(LIST_STATEMENTS);
    }

    @Test
    void filtersByTheStatusOfTheAccountAndShowsTheLastAccess() throws Exception {
        list("q", word, "accountStatus", "NONE")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Delta " + word, "Zeta " + word)));
        list("q", word, "accountStatus", "PENDING_ACTIVATION")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Alfa " + capitalized(),
                        "Epsilon " + word)));
        list("q", word, "accountStatus", "ACTIVE")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Beta " + capitalized()))
                .andExpect(jsonPath("$.content[0].lastLoginAt").isNotEmpty());
        list("q", word, "accountStatus", "INACTIVE")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Gama " + capitalized())));

        // Combined with the role and the type
        list("q", word, "role", "OPERATIONS_DIRECTOR", "accountStatus", "ACTIVE")
                .andExpect(jsonPath("$.totalElements").value(1));
        list("q", word, "role", "PHYSIOTHERAPIST", "accountStatus", "ACTIVE")
                .andExpect(jsonPath("$.totalElements").value(0));
        list("q", word, "type", "STAFF", "accountStatus", "NONE")
                .andExpect(jsonPath("$.totalElements").value(0));
        list("q", word, "type", "PATIENT", "accountStatus", "PENDING_ACTIVATION")
                .andExpect(jsonPath("$.content[*].lastName").value(contains("Epsilon " + word)));
        list("q", word, "accountStatus", "BLOCKED").andExpect(status().isBadRequest());

        // Nobody else has logged in
        list("q", word).andExpect(jsonPath("$.content[?(@.lastLoginAt != null)].lastName")
                .value(contains("Beta " + capitalized())));
    }

    @Test
    void theIndicatorsCountTheActiveAccountsAndTheAccountsOfEachRole() throws Exception {
        String count = "SELECT count(*) FROM users WHERE role = ?";
        long active = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE active AND email_verified_at IS NOT NULL", Long.class);

        mockMvc.perform(get("/api/people/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAccounts").value(active))
                .andExpect(jsonPath("$.physiotherapists")
                        .value(jdbc.queryForObject(count, Long.class, "PHYSIOTHERAPIST")))
                .andExpect(jsonPath("$.portalPatients").value(jdbc.queryForObject(count, Long.class, "PATIENT")))
                .andExpect(jsonPath("$.administrators")
                        .value(jdbc.queryForObject(count, Long.class, "ADMINISTRATOR")));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/people/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        // Token validation and one GROUP BY
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void aFilteredPageNeedsOneMoreStatement() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        list("q", word, "accountStatus", "PENDING_ACTIVATION").andExpect(status().isOk());

        statistics.clear();
        list("q", word, "accountStatus", "PENDING_ACTIVATION").andExpect(status().isOk());

        // The accounts with the status, then the same six statements
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(LIST_STATEMENTS + 1);
    }

    /** Registers a staff member whose last name has the word of the test, and returns its account. */
    private Long registerStaff(String role, String surname) throws Exception {
        String body = TestStaff.register(mockMvc, adminToken, TestStaff.json(role, TestPatients.randomNationalId(),
                        "Persona", surname + " " + word, uniqueEmail(surname.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.account.userId")).longValue();
    }

    private ResultActions list(String... parameters) throws Exception {
        var request = get("/api/people").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
        }
        return mockMvc.perform(request);
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
