package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-10: the sequence number is the highest of the treatment + 1, read while the treatment row is locked, so
 * simultaneous registrations on the same treatment never get the same number (the unique constraint would reject
 * the second one with 409).
 */
@AutoConfigureMockMvc
class TherapySessionConcurrencyIntegrationTests extends AbstractIntegrationTest {

    private static final int REQUESTS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousRegistrationsGetConsecutiveSequenceNumbersWithoutRepeats() throws Exception {
        Long treatmentId = TestTreatments.insertActive(jdbc);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tokens.add(tokenService.issue(createPhysio(), Instant.now()).token());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String token : tokens) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/treatments/{id}/therapy-sessions", treatmentId)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(5)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            for (Future<Integer> status : statuses) {
                assertThat(status.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForList(
                "SELECT sequence_number FROM therapy_sessions WHERE treatment_id = ? ORDER BY sequence_number",
                Integer.class, treatmentId))
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    private User createPhysio() {
        return userRepository.save(User.create("concurrencia-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Fisioterapeuta de prueba", Role.PHYSIOTHERAPIST, null, Instant.now()));
    }

}
