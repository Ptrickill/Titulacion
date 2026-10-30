package ec.rehabelite.clinicalrecords.web;

import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.recordBody;
import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.treatmentBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-18: Hibernate Envers keeps every version of a treatment in treatments_aud, and revinfo records when and who
 * made each change (no one for changes without an authenticated user).
 */
@AutoConfigureMockMvc
class TreatmentVersionsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TreatmentRepository treatmentRepository;

    private User physio;
    private Long treatmentId;

    @BeforeEach
    void setUp() throws Exception {
        physio = createUser("Lcda. Valeria Andrade");
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        mockMvc.perform(post("/api/patients/{id}/clinical-record", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(physio))
                        .contentType(MediaType.APPLICATION_JSON).content(recordBody(treatmentBody(6, 12, "M54.5"))))
                .andExpect(status().isCreated());
        treatmentId = jdbc.queryForObject("""
                SELECT t.id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                WHERE r.patient_id = ?""", Long.class, patientId);
    }

    @Test
    void aStatusChangeKeepsThePreviousVersionOfTheTreatment() throws Exception {
        User other = createUser("Lcdo. Mateo Ríos");

        mockMvc.perform(patch("/api/treatments/{id}/status", treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"COMPLETED\",\"reason\":\"Alta por mejoría\"}"))
                .andExpect(status().isOk());

        transactionTemplate.executeWithoutResult(tx -> {
            AuditReader reader = AuditReaderFactory.get(entityManager);
            List<Number> revisions = reader.getRevisions(Treatment.class, treatmentId);
            assertThat(revisions).hasSize(2);
            Treatment created = reader.find(Treatment.class, treatmentId, revisions.get(0));
            Treatment completed = reader.find(Treatment.class, treatmentId, revisions.get(1));
            assertThat(created.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
            assertThat(created.getUpdatedBy()).isNull();
            assertThat(created.getPlannedSessions()).isEqualTo(12);
            assertThat(completed.getStatus()).isEqualTo(TreatmentStatus.COMPLETED);
            assertThat(completed.getUpdatedBy()).isEqualTo(other.getId());
        });

        // Insert (0) by the author of the record, update (1) by whoever changed the status
        assertThat(jdbc.queryForList("""
                SELECT a.revtype, a.status, r.user_id, r.revision_timestamp IS NOT NULL AS timed
                FROM treatments_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = ? ORDER BY a.rev""", treatmentId))
                .containsExactly(
                        Map.of("revtype", 0, "status", "ACTIVE", "user_id", physio.getId(), "timed", true),
                        Map.of("revtype", 1, "status", "COMPLETED", "user_id", other.getId(), "timed", true));
        assertThat(jdbc.queryForObject("SELECT status FROM treatments WHERE id = ?", String.class, treatmentId))
                .isEqualTo("COMPLETED");
    }

    @Test
    void aChangeWithoutAnAuthenticatedUserIsVersionedWithoutAuthor() {
        SecurityContextHolder.clearContext();

        // E.g. a startup initializer: no request and no token
        transactionTemplate.executeWithoutResult(tx -> treatmentRepository.findById(treatmentId).orElseThrow()
                .changeStatus(TreatmentStatus.ABANDONED, "Cierre automático", physio.getId(), Instant.now()));

        Map<String, Object> latest = jdbc.queryForMap("""
                SELECT a.status, r.user_id FROM treatments_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = ? ORDER BY a.rev DESC LIMIT 1""", treatmentId);
        assertThat(latest).containsEntry("status", "ABANDONED");
        assertThat(latest.get("user_id")).isNull();
    }

    private User createUser(String fullName) {
        return userRepository.save(User.create("versiones-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, Role.PHYSIOTHERAPIST, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
