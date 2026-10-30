package ec.rehabelite.clinicalrecords.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-09: the clinical record answers in under 2 seconds and with a bounded number of SQL statements. 200 patients
 * with 5 treatments each (4 closed, 1 active with a handed-over responsible) are inserted with SQL; every record is
 * read through the API while Hibernate counts the statements it prepares.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClinicalRecordViewPerformanceIntegrationTests extends AbstractIntegrationTest {

    private static final int PATIENTS = 200;
    private static final int TREATMENTS_PER_PATIENT = 5;
    private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(2);
    /**
     * Token check (account still active), patient, record with treatments and diagnoses, current responsibles,
     * their names and the READ audit entry.
     */
    private static final long MAX_STATEMENTS = 6;

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

    private String physioToken;
    private Long firstPhysioId;
    private Long secondPhysioId;
    private final List<Long> patientIds = new ArrayList<>();

    @BeforeAll
    void insertPatientsWithTheirTreatments() {
        User physio = createPhysiotherapist("Lcda. Valeria Andrade");
        firstPhysioId = physio.getId();
        secondPhysioId = createPhysiotherapist("Lcdo. Mateo Ríos").getId();
        physioToken = tokenService.issue(physio, Instant.now()).token();
        for (int i = 0; i < PATIENTS; i++) {
            Long patientId = TestPatients.insert(jdbc, "Paciente " + i, "Rendimiento Prueba");
            insertRecord(patientId, TREATMENTS_PER_PATIENT);
            patientIds.add(patientId);
        }
    }

    @Test
    void everyRecordAnswersInUnderTwoSecondsWithABoundedNumberOfStatements() throws Exception {
        Statistics statistics = statistics();
        long slowest = 0;
        long mostStatements = 0;

        for (Long patientId : patientIds) {
            statistics.clear();
            long start = System.nanoTime();
            mockMvc.perform(get("/api/patients/{id}/clinical-record", patientId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeTreatment.currentResponsible.fullName").value("Lcdo. Mateo Ríos"))
                    .andExpect(jsonPath("$.previousTreatments.length()").value(TREATMENTS_PER_PATIENT - 1));
            slowest = Math.max(slowest, System.nanoTime() - start);
            mostStatements = Math.max(mostStatements, statistics.getPrepareStatementCount());
        }

        assertThat(Duration.ofNanos(slowest)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(mostStatements).isLessThanOrEqualTo(MAX_STATEMENTS);    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheTreatments() throws Exception {
        Long withOneTreatment = TestPatients.insert(jdbc, "Paciente", "Un Tratamiento");
        insertRecord(withOneTreatment, 1);

        long withOne = statementsToRead(withOneTreatment);
        long withFive = statementsToRead(patientIds.getFirst());

        assertThat(withFive).isEqualTo(withOne);
    }

    private long statementsToRead(Long patientId) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/patients/{id}/clinical-record", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /**
     * A record whose last treatment is active and was handed over from the first physiotherapist to the second; the
     * earlier ones are completed, each with its responsible.
     */
    private void insertRecord(Long patientId, int treatments) {
        Long recordId = jdbc.queryForObject("""
                INSERT INTO clinical_records (patient_id, habits, physical_activity, allergies, current_medication,
                                              personal_history, surgical_history, family_history, created_by)
                VALUES (?, 'No fuma', 'Camina', 'Ninguna', 'Ninguna', 'Ninguno', 'Ninguno', 'Ninguno', ?)
                RETURNING id""", Long.class, patientId, firstPhysioId);
        jdbc.update("""
                INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness,
                                        referring_physician, complementary_tests, severity, pain_type, pain_location,
                                        initial_pain_score, pain_frequency, session_frequency, goal, planned_sessions,
                                        status, start_date, created_by)
                SELECT ?, 'M54.5', 'Dolor lumbar', 'Tres semanas', 'Dr. Andrade', 'Radiografía', 'MODERATE',
                       'Punzante', 'Lumbar', 6, 'Intermitente', '3 por semana', 'Reducir el dolor', 10,
                       CASE WHEN n = ? THEN 'ACTIVE' ELSE 'COMPLETED' END, DATE '2024-01-01' + n * 60, ?
                FROM generate_series(1, ?) AS n""", recordId, treatments, firstPhysioId, treatments);
        jdbc.update("""
                INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from, valid_to)
                SELECT t.id, ?, now() - interval '30 days',
                       CASE WHEN t.status = 'ACTIVE' THEN now() - interval '10 days' END
                FROM treatments t WHERE t.clinical_record_id = ?""", firstPhysioId, recordId);
        jdbc.update("""
                INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from)
                SELECT t.id, ?, now() - interval '10 days'
                FROM treatments t WHERE t.clinical_record_id = ? AND t.status = 'ACTIVE'""", secondPhysioId, recordId);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private User createPhysiotherapist(String fullName) {
        return userRepository.save(User.create("rendimiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, Role.PHYSIOTHERAPIST, null, Instant.now()));
    }

}
