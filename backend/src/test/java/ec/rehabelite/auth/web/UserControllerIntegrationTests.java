package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/users since MEJ-02: only the actions on an account that the people registry and its records use (status and
 * resending its link). There is no list of accounts nor a way to create one by typing a name and an email: accounts
 * come from the people registry (StaffRegistrationIntegrationTests and PortalAccountIntegrationTests) and are listed
 * there (PeopleRegistryIntegrationTests).
 */
@AutoConfigureMockMvc
class UserControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.create(uniqueEmail("admin"), "not-a-real-hash", "Admin de prueba",
                Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
    }

    @Test
    void thereIsNoListOfAccountsNorACreationByTypingANameAndAnEmail() throws Exception {
        String email = uniqueEmail("a-mano");

        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"fullName\":\"Escrito A Mano\",\"role\":\"PHYSIOTHERAPIST\"}"
                                .formatted(email)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void theAccountActionsAnswerTheAccountWithItsStatusAndWithoutPasswordHash() throws Exception {
        Long userId = TestStaff.registerAccount(mockMvc, adminToken, "OPERATIONS_DIRECTOR", uniqueEmail("cuenta"));

        mockMvc.perform(post("/api/users/{id}/resend-invitation", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.fullName").value("Cuenta Nueva Prueba"))
                .andExpect(jsonPath("$.emailChangePending").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(patch("/api/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void onlyTheAdministratorManagesAccounts() throws Exception {
        User physio = userRepository.save(User.create(uniqueEmail("fisio"), "not-a-real-hash", "Fisio",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        String token = tokenService.issue(physio, Instant.now()).token();

        mockMvc.perform(patch("/api/users/{id}/status", physio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/{id}/resend-invitation", physio.getId())).andExpect(status().isUnauthorized());
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
