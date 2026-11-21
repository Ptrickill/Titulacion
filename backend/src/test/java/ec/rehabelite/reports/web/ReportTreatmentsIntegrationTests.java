package ec.rehabelite.reports.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-14: GET /api/reports/treatments lists the treatments filtered by status and by current responsible, with the name
 * of the patient and nothing else of their personal data (LOPDP); GET /api/reports/physiotherapists feeds the filter.
 */
@AutoConfigureMockMvc
class ReportTreatmentsIntegrationTests extends AbstractIntegrationTest {

    private static final String TREATMENTS = "/api/reports/treatments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User director;
    private Long valeria;
    private LocalDate today;
    private Long rosaPatient;
    private Long active;
    private Long abandoned;
    private Long completed;

    @BeforeEach
    void setUp() {
        director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Operativa");
        valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        today = LocalDate.now();
        rosaPatient = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        completed = treatmentOf(rosaPatient, "COMPLETED", 300, valeria);
        active = treatmentOf(TestPatients.insert(jdbc, "Luis Alberto", "Quishpe Tipán"), "ACTIVE", 10, valeria);
        abandoned = treatmentOf(TestPatients.insert(jdbc, "Daniela Sofía", "Zambrano Loor"), "ABANDONED", 100,
                valeria);
    }

    @Test
    void theTreatmentsOfAResponsibleComeFromTheMostRecentStartDateWithTheNameOfThePatient() throws Exception {
        list(Map.of("physiotherapistId", valeria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(
                        active.intValue(), abandoned.intValue(), completed.intValue())))
                .andExpect(jsonPath("$.content[0].patientName").value("Luis Alberto Quishpe Tipán"))
                .andExpect(jsonPath("$.content[0].diagnosis.code").value("M54.5"))
                .andExpect(jsonPath("$.content[0].diagnosis.description").value("Lumbago no especificado"))
                .andExpect(jsonPath("$.content[0].responsibleName").value("Valeria Andrade"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].startDate").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.content[2].patientName").value("Rosa Elena Ortiz Vega"))
                .andExpect(jsonPath("$.content[2].status").value("COMPLETED"));
    }

    @Test
    void theListHasOnlyTheNameOfThePatientNeverTheNationalIdNorOtherPersonalData() throws Exception {
        String body = list(Map.of("physiotherapistId", valeria)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> content = JsonPath.read(body, "$.content");
        assertThat(content).allSatisfy(row -> assertThat(row).containsOnlyKeys("id", "patientName", "diagnosis",
                "responsibleName", "status", "startDate"));
        assertThat(body)
                .doesNotContain(TestPatients.nationalIdOf(jdbc, rosaPatient))
                .doesNotContain("nationalId", "medicalRecordNumber", "patientId", "birthDate", "1985-04-12",
                        "0990000000", "paciente@rehabelite.test", "Dirección de prueba")
                // Nothing of the clinical content of the treatment either
                .doesNotContain("Dolor lumbar", "Reducir el dolor", "chiefComplaint");
    }

    @Test
    void theStatusFiltersTheListAloneOrWithTheResponsible() throws Exception {
        list(Map.of("status", "ABANDONED", "physiotherapistId", valeria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(abandoned));

        String onlyActive = list(Map.of("status", "ACTIVE", "size", 50)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(onlyActive, "$.content[*].status");
        assertThat(statuses).isNotEmpty().containsOnly("ACTIVE");
    }

    @Test
    void theListIsPaginatedAndEachPageAuditsOnlyItsTreatmentsAsRead() throws Exception {
        list(Map.of("physiotherapistId", valeria, "size", 2, "page", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(completed))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        assertThat(auditEntries()).singleElement().satisfies(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Treatment")
                .containsEntry("entity_id", completed)
                .containsEntry("ip_address", "127.0.0.1"));

        list(Map.of("physiotherapistId", valeria)).andExpect(status().isOk());
        assertThat(auditEntries()).hasSize(4);
    }

    @Test
    void aTreatmentWithoutResponsibleHasNoName() throws Exception {
        Long withoutResponsible = TestTreatments.insert(jdbc, "COMPLETED", today.plusDays(7300));

        list(Map.of("status", "COMPLETED", "size", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(withoutResponsible))
                .andExpect(jsonPath("$.content[0].responsibleName").doesNotExist());
    }

    @Test
    void aResponsibleWithoutTreatmentsHasAnEmptyListAndNothingIsAudited() throws Exception {
        Long newcomer = createUser(Role.PHYSIOTHERAPIST, "Julián Rivas").getId();

        list(Map.of("physiotherapistId", newcomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        assertThat(auditEntries()).isEmpty();
    }

    @Test
    void anInvalidStatusPageOrSizeIsRejectedWith400() throws Exception {
        list(Map.of("status", "PAUSED")).andExpect(status().isBadRequest());
        list(Map.of("size", 51)).andExpect(status().isBadRequest());
        list(Map.of("size", 0)).andExpect(status().isBadRequest());
        list(Map.of("page", -1)).andExpect(status().isBadRequest());

        assertThat(auditEntries()).isEmpty();
    }

    @Test
    void theOptionsOfTheFilterAreEveryPhysiotherapistActiveOrNotAndAreNotAudited() throws Exception {
        User deactivated = createUser(Role.PHYSIOTHERAPIST, "Aaa Fisioterapeuta Desactivada");
        jdbc.update("UPDATE users SET active = false WHERE id = ?", deactivated.getId());

        String body = perform(get("/api/reports/physiotherapists")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> options = JsonPath.read(body, "$");
        assertThat(options).allSatisfy(option -> assertThat(option).containsOnlyKeys("id", "fullName"));
        List<Integer> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(deactivated.getId().intValue(), valeria.intValue())
                .doesNotContain(director.getId().intValue());
        assertThat(auditEntries()).isEmpty();
    }

    // ----- helpers -----

    /** A treatment of the patient that started the given days ago, with the physiotherapist as responsible. */
    private Long treatmentOf(Long patientId, String status, int daysAgo, Long physiotherapistId) {
        Long treatmentId = TestTreatments.insertFor(jdbc, TestTreatments.insertRecord(jdbc, patientId), status,
                today.minusDays(daysAgo), 7, 12);
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        return treatmentId;
    }

    private ResultActions list(Map<String, Object> parameters) throws Exception {
        MockHttpServletRequestBuilder request = get(TREATMENTS);
        parameters.forEach((name, value) -> request.param(name, value.toString()));
        return perform(request);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(director, Instant.now()).token()));
    }

    private List<Map<String, Object>> auditEntries() {
        return jdbc.queryForList("SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE user_id = ?",
                director.getId());
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("reportes-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
