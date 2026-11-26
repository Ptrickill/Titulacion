package ec.rehabelite.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.InvalidCredentialsException;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-03 and MEJ-05 end to end: PATCH /api/users/{id}/status activates and deactivates accounts. The operations
 * director manages the staff accounts and the reception the patients' portal accounts; any other combination is a
 * 403, and the last active operations director is never deactivated.
 */
@AutoConfigureMockMvc
class UserStatusIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Clave.Inicial2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User director;
    private String directorToken;

    @BeforeEach
    void setUp() {
        director = createUser(Role.OPERATIONS_DIRECTOR);
        directorToken = tokenOf(director);
    }

    @Test
    void deactivatedAccountCannotLogInAndTheChangeIsAudited() throws Exception {
        User physio = createUser(Role.PHYSIOTHERAPIST);

        changeStatus(directorToken, physio.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(physio.getId()))
                .andExpect(jsonPath("$.active").value(false));

        login(physio.getEmail())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(InvalidCredentialsException.USER_MESSAGE));
        User stored = userRepository.findById(physio.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getUpdatedBy()).isEqualTo(director.getId());
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(statusChangeAuthors(physio.getId())).containsExactly(director.getId());
    }

    @Test
    void reactivatedAccountCanLogInAgain() throws Exception {
        User receptionist = createUser(Role.ADMINISTRATOR);
        changeStatus(directorToken, receptionist.getId(), false).andExpect(status().isOk());
        login(receptionist.getEmail()).andExpect(status().isUnauthorized());

        changeStatus(directorToken, receptionist.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        login(receptionist.getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMINISTRATOR"));
        assertThat(statusChangeAuthors(receptionist.getId())).containsExactly(director.getId(), director.getId());
    }

    @Test
    void tokensIssuedBeforeTheDeactivationStopWorking() throws Exception {
        User otherDirector = createUser(Role.OPERATIONS_DIRECTOR);
        String otherToken = tokenOf(otherDirector);
        staffArea(otherToken).andExpect(status().isOk());

        changeStatus(directorToken, otherDirector.getId(), false).andExpect(status().isOk());

        staffArea(otherToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Debe iniciar sesión para acceder a este recurso."));
        changeStatus(directorToken, otherDirector.getId(), true).andExpect(status().isOk());
        staffArea(otherToken).andExpect(status().isOk());
    }

    @Test
    void directorCannotDeactivateTheirOwnAccount() throws Exception {
        changeStatus(directorToken, director.getId(), false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("No puede desactivar su propia cuenta."));

        assertThat(userRepository.findById(director.getId()).orElseThrow().isActive()).isTrue();
        assertThat(statusChangeAuthors(director.getId())).isEmpty();
        staffArea(directorToken).andExpect(status().isOk());
    }

    @Test
    void theReceptionManagesThePortalAccountsOfThePatients() throws Exception {
        User receptionist = createUser(Role.ADMINISTRATOR);
        User patient = createUser(Role.PATIENT);

        changeStatus(tokenOf(receptionist), patient.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(statusChangeAuthors(patient.getId())).containsExactly(receptionist.getId());
    }

    @Test
    void eachSideIsForbiddenTheAccountsOfTheOtherAndTheDenialIsAudited() throws Exception {
        String receptionToken = tokenOf(createUser(Role.ADMINISTRATOR));
        User physio = createUser(Role.PHYSIOTHERAPIST);
        User otherReceptionist = createUser(Role.ADMINISTRATOR);
        User patient = createUser(Role.PATIENT);

        for (User staffAccount : List.of(physio, otherReceptionist, director)) {
            changeStatus(receptionToken, staffAccount.getId(), false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("No puede administrar esa cuenta."));
            assertThat(accessDenied(staffAccount.getId())).isEqualTo(1);
        }
        changeStatus(directorToken, patient.getId(), false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No puede administrar esa cuenta."));
        mockMvc.perform(post("/api/users/{id}/resend-invitation", patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken))
                .andExpect(status().isForbidden());

        for (User account : List.of(physio, otherReceptionist, director, patient)) {
            assertThat(userRepository.findById(account.getId()).orElseThrow().isActive()).isTrue();
            assertThat(statusChangeAuthors(account.getId())).isEmpty();
        }
    }

    /**
     * Two directors deactivating each other at the same time, while they are the only active directors: the
     * directors are locked first, so exactly one of them succeeds and an active director always remains. The other
     * request answers 422 (it waited for the lock) or 401 (its token stopped working before it started).
     */
    @Test
    void twoDirectorsDeactivatingEachOtherAtTheSameTimeLeaveOneActiveDirector() throws Exception {
        User other = createUser(Role.OPERATIONS_DIRECTOR);
        String otherToken = tokenOf(other);
        // Only these two directors stay active during the test; the others are reactivated at the end
        List<Long> deactivated = jdbc.queryForList("""
                UPDATE users SET active = false
                WHERE role = 'OPERATIONS_DIRECTOR' AND active AND email_verified_at IS NOT NULL AND id NOT IN (?, ?)
                RETURNING id""", Long.class, director.getId(), other.getId());
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = threads.submit(() -> {
                start.await();
                return changeStatus(directorToken, other.getId(), false).andReturn().getResponse().getStatus();
            });
            Future<Integer> second = threads.submit(() -> {
                start.await();
                return changeStatus(otherToken, director.getId(), false).andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(statuses).containsOnlyOnce(200);
            assertThat(statuses).filteredOn(code -> code != 200).singleElement().isIn(401, 422);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM users
                    WHERE id IN (?, ?) AND active AND email_verified_at IS NOT NULL""", Integer.class,
                    director.getId(), other.getId())).isEqualTo(1);
        } finally {
            threads.shutdownNow();
            deactivated.forEach(id -> jdbc.update("UPDATE users SET active = true WHERE id = ?", id));
        }
    }

    @Test
    void askingForTheCurrentStatusIsNotAudited() throws Exception {
        User physio = createUser(Role.PHYSIOTHERAPIST);

        changeStatus(directorToken, physio.getId(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(statusChangeAuthors(physio.getId())).isEmpty();
    }

    @Test
    void unknownAccountOrMissingStatusIsRejected() throws Exception {
        changeStatus(directorToken, 999_999_999L, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El usuario no existe."));

        User physio = createUser(Role.PHYSIOTHERAPIST);
        mockMvc.perform(patch("/api/users/{id}/status", physio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("active"));
        assertThat(userRepository.findById(physio.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void physiotherapistsAndPatientsNeverChangeTheStatus() throws Exception {
        User target = createUser(Role.PATIENT);

        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.PATIENT)) {
            changeStatus(tokenOf(createUser(role)), target.getId(), false).andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/users/{id}/status", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
        assertThat(statusChangeAuthors(target.getId())).isEmpty();
    }

    @Test
    void openApiDocumentsTheStatusEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/{id}/status'].patch.tags[0]").value("Usuarios"))
                .andExpect(jsonPath("$.paths['/api/users/{id}/status'].patch.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}/status'].patch.responses['403']").exists());
    }

    private ResultActions changeStatus(String token, Long userId, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/users/{id}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":%s}".formatted(active)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
    }

    /** Any call of the operations director's staff area (MEJ-05). */
    private ResultActions staffArea(String token) throws Exception {
        return mockMvc.perform(get("/api/staff/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private List<Long> statusChangeAuthors(Long userId) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs
                WHERE action = 'STATUS_CHANGE' AND entity_type = 'User' AND entity_id = ?
                ORDER BY id""", Long.class, userId);
    }

    private Integer accessDenied(Long userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action = 'ACCESS_DENIED' AND entity_type = 'users' AND entity_id = ?""", Integer.class, userId);
    }

    private User createUser(Role role) {
        String email = "estado-" + UUID.randomUUID() + "@rehabelite.test";
        return userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), "Usuario de prueba", role,
                null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
