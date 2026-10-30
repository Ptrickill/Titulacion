package ec.rehabelite.patients.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
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
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-06: GET /api/patients searches by name (partial, ignoring accents and case) or by the beginning of the
 * national ID, ordered by last name and paginated. Other tests share the database, so every patient here carries a
 * unique word ({@link #tag}) in its last name and every name query includes it.
 */
@AutoConfigureMockMvc
class PatientSearchIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    /** Letters only, so a query with it is never taken as a national ID. */
    private String tag;

    @BeforeEach
    void setUp() {
        adminToken = tokenOf(Role.ADMINISTRATOR);
        tag = "Zq" + ThreadLocalRandom.current().ints(8, 'a', 'z' + 1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
    }

    @Test
    void findsNamesIgnoringAccentsAndCase() throws Exception {
        Long perez = TestPatients.insert(jdbc, "Juan Carlos", "Pérez " + tag);
        Long nunez = TestPatients.insert(jdbc, "María José", "Núñez " + tag);

        search("perez " + tag, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(perez))
                .andExpect(jsonPath("$.content[0].lastName").value("Pérez " + tag));
        search("PÉREZ " + tag.toUpperCase(), adminToken)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(perez.intValue())));
        search("nunez " + tag, adminToken)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(nunez.intValue())));
        search("jose " + tag, adminToken)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(nunez.intValue())));
    }

    @Test
    void wordsArePartialAndMayComeInAnyOrder() throws Exception {
        Long patient = TestPatients.insert(jdbc, "Juan Carlos", "Pérez " + tag);

        for (String query : new String[] {"juan perez " + tag, tag + " juan", "car pér " + tag,
                "  " + tag.substring(0, 7) + "   jua "}) {
            search(query, adminToken)
                    .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(patient.intValue())));
        }
        search("pedro " + tag, adminToken).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void findsByTheBeginningOfTheNationalId() throws Exception {
        Long patient = TestPatients.insert(jdbc, "Elena", tag);
        String nationalId = TestPatients.nationalIdOf(jdbc, patient);

        search(nationalId.substring(0, 6), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(patient.intValue())))
                .andExpect(jsonPath("$.content[*].nationalId").value(everyItem(startsWith(nationalId.substring(0, 6)))));
        search(nationalId, adminToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].medicalRecordNumber").value(nationalId));
        // Digits from the middle of the national ID are not a prefix
        search(nationalId.substring(3), adminToken)
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(hasItem(patient.intValue()))));
    }

    @Test
    void resultsAreOrderedByLastNameAndPaginated() throws Exception {
        for (String letter : new String[] {"Duarte", "Álvarez", "Espinoza", "Benítez", "Cárdenas"}) {
            TestPatients.insert(jdbc, "Ana", tag + " " + letter);
        }

        search(tag, adminToken, 0, 2)
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value(tag + " Álvarez"))
                .andExpect(jsonPath("$.content[1].lastName").value(tag + " Benítez"));
        search(tag, adminToken, 1, 2)
                .andExpect(jsonPath("$.content[0].lastName").value(tag + " Cárdenas"))
                .andExpect(jsonPath("$.content[1].lastName").value(tag + " Duarte"));
        search(tag, adminToken, 2, 2)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value(tag + " Espinoza"));
    }

    @Test
    void likeWildcardsAreLiteralAndAnEmptyQueryListsEveryone() throws Exception {
        TestPatients.insert(jdbc, "Elena", tag);

        search("%" + tag + "_", adminToken).andExpect(jsonPath("$.totalElements").value(1));
        search("", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(jdbc.queryForObject("SELECT count(*) FROM patients",
                        Integer.class)));
    }

    @Test
    void resultsCarryNoClinicalData() throws Exception {
        TestPatients.insert(jdbc, "Elena", tag);

        search(tag, adminToken)
                .andExpect(jsonPath("$.content[0].age").isNumber())
                .andExpect(jsonPath("$.content[0].phone").exists())
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].length()").value(8))
                .andExpect(jsonPath("$.content[0].diagnosis").doesNotExist())
                .andExpect(jsonPath("$.content[0].treatments").doesNotExist());
    }

    @Test
    void pageAndSizeAreValidated() throws Exception {
        search(tag, adminToken, -1, 10).andExpect(status().isBadRequest());
        search(tag, adminToken, 0, 0).andExpect(status().isBadRequest());
        search(tag, adminToken, 0, 51)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"));
    }

    @Test
    void administratorsAndPhysiotherapistsSearch() throws Exception {
        search(tag, tokenOf(Role.PHYSIOTHERAPIST)).andExpect(status().isOk());
        search(tag, tokenOf(Role.PATIENT)).andExpect(status().isForbidden());
        search(tag, tokenOf(Role.OPERATIONS_DIRECTOR)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsSearchAndUpdate() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/patients'].get.tags[0]").value("Pacientes"))
                .andExpect(jsonPath("$.paths['/api/patients'].get.parameters[*].name")
                        .value(containsInAnyOrder("q", "page", "size")))
                .andExpect(jsonPath("$.paths['/api/patients/{id}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/patients/{id}'].put.responses['422']").exists());
    }

    private ResultActions search(String query, String token) throws Exception {
        return mockMvc.perform(get("/api/patients").param("q", query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions search(String query, String token, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/patients").param("q", query)
                .param("page", String.valueOf(page)).param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String tokenOf(Role role) {
        User user = userRepository.save(User.create("busqueda-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
        return tokenService.issue(user, Instant.now()).token();
    }

}
