package ec.rehabelite.assignments.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestStaff;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MEJ-04: the load of every physiotherapist is one GROUP BY, and the director's board, availability and treatment
 * list keep the same number of SQL statements with a few or many physiotherapists, slots and treatments (no N+1).
 * Hibernate counts the statements it prepares (hibernate.generate_statistics in the test profile).
 */
@AutoConfigureMockMvc
class OperationsPerformanceIntegrationTests extends AbstractIntegrationTest {

    /**
     * Token check, physiotherapist accounts, their staff records, their slots, the active accounts and the loads.
     */
    private static final long BOARD_STATEMENTS = 6;
    /** The same as the board: the range is checked in memory. */
    private static final long AVAILABILITY_STATEMENTS = 6;
    /**
     * Token check, active accounts, page, total, names of the patients, names of the responsibles, days of their
     * schedules and the READ entries of the page in one INSERT.
     */
    private static final long LIST_STATEMENTS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TreatmentQueryPort treatmentQueries;

    @Test
    void theLoadOfEveryPhysiotherapistIsOneStatement() {
        User physio = physiotherapistWithTreatments(3);
        Statistics statistics = statistics();

        statistics.clear();
        Map<Long, Long> loads = treatmentQueries.activeLoadByResponsible();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(loads).containsEntry(physio.getId(), 3L);
    }

    @Test
    void theBoardTheAvailabilityAndTheListDoNotGrowWithThePhysiotherapistsNorTheTreatments() throws Exception {
        User director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Rendimiento");
        User inactive = physiotherapistWithTreatments(2);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", inactive.getId());
        physiotherapistWithTreatments(1);
        long board = statements(director, get("/api/operations/schedules"));
        long availability = statements(director, availabilityRequest());
        long list = statements(director, listRequest(1));

        for (int i = 0; i < 15; i++) {
            physiotherapistWithTreatments(4);
        }

        assertThat(board).isEqualTo(BOARD_STATEMENTS);
        assertThat(availability).isEqualTo(AVAILABILITY_STATEMENTS);
        assertThat(list).isEqualTo(LIST_STATEMENTS);
        assertThat(statements(director, get("/api/operations/schedules"))).isEqualTo(board);
        assertThat(statements(director, availabilityRequest())).isEqualTo(availability);
        assertThat(statements(director, listRequest(50))).isEqualTo(list);
        // The inactive-responsible filter adds one statement: every physiotherapist account
        assertThat(statements(director, listRequest(1).param("inactiveResponsible", "true"))).isEqualTo(list + 1);
    }

    /**
     * A full page, so the total is counted too (Spring Data skips the count when the first page is not full). With
     * size 50 the shared database has more than 50 active treatments by then.
     */
    private static MockHttpServletRequestBuilder listRequest(int size) {
        return get("/api/operations/treatments").param("size", String.valueOf(size));
    }

    private static MockHttpServletRequestBuilder availabilityRequest() {
        return get("/api/operations/availability").param("day", "TUESDAY").param("from", "09:00").param("to", "11:00");
    }

    private long statements(User director, MockHttpServletRequestBuilder request) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(director, Instant.now()).token())).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** An active physiotherapist with a staff record, Tuesday 08:00-12:00 and the given ACTIVE treatments. */
    private User physiotherapistWithTreatments(int treatments) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User physio = createUser(Role.PHYSIOTHERAPIST, "Fisio " + suffix);
        Long staffId = TestStaff.insertRecord(jdbc, physio.getId(), "Fisio", "Rendimiento " + suffix);
        TestStaff.insertSlots(jdbc, staffId, "2 08:00 12:00", "4 14:00 18:00");
        for (int i = 0; i < treatments; i++) {
            Long treatmentId = TestTreatments.insert(jdbc, "ACTIVE", LocalDate.now().minusDays(i));
            TestTreatments.assignResponsible(jdbc, treatmentId, physio.getId());
        }
        return physio;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("operaciones-rendimiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
