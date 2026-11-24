package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * "Gestión de Usuarios" (HU-02, HU-03, MEJ-02): /api/users lists and administers accounts. Since MEJ-02 no account is
 * created there by typing a name and an email: they come from the people registry (StaffRegistrationIntegrationTests
 * and PortalAccountIntegrationTests).
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
    void accountsCanNoLongerBeCreatedByTypingANameAndAnEmail() throws Exception {
        String email = uniqueEmail("a-mano");

        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"fullName\":\"Escrito A Mano\",\"role\":\"PHYSIOTHERAPIST\"}"
                                .formatted(email)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void listsAccountsWithTheirStatusAndWithoutPasswordHashes() throws Exception {
        String email = uniqueEmail("listado");
        Long userId = TestStaff.registerAccount(mockMvc, adminToken, "OPERATIONS_DIRECTOR", email);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(hasItem(email)))
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(userId)).value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$[?(@.id == %d)].emailChangePending".formatted(userId)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].fullName".formatted(userId)).value("Cuenta Nueva Prueba"))
                .andExpect(jsonPath("$[*].passwordHash").isEmpty())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString())
                        .doesNotContain("$2a$").doesNotContain("$2b$"));
    }

    @Test
    void onlyTheAdministratorManagesAccounts() throws Exception {
        User physio = userRepository.save(User.create(uniqueEmail("fisio"), "not-a-real-hash", "Fisio",
                Role.PHYSIOTHERAPIST, null, Instant.now()));
        String token = tokenService.issue(physio, Instant.now()).token();

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@rehabelite.test";
    }

}
