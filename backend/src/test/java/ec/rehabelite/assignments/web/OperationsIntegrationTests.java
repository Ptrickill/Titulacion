package ec.rehabelite.assignments.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestStaff;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MEJ-04 end to end for the operations director: the schedules board with the loads, the availability (only active
 * accounts, one slot covering the range, by load and then last names), the list of active treatments without clinical
 * content and with the inactive-responsible flag, and the reassignment with its audit entry and Envers revision.
 * <p>
 * The database is shared with other tests, so every assertion looks only at this test's physiotherapists and
 * treatments: unique last names, and treatments that start years ahead so they are on the first page of the list.
 */
@AutoConfigureMockMvc
class OperationsIntegrationTests extends AbstractIntegrationTest {

    /** The only properties of a treatment of the director's list: never a clinical one (LOPDP). */
    private static final List<String> TREATMENT_PROPERTIES =
            List.of("id", "patientName", "startDate", "responsible", "responsibleInactive", "scheduleDays");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private String suffix;
    private User director;
    private User alfa;
    private User beta;
    private User zeta;
    private User partial;
    private User inactive;
    private Long betaStaff;
    private Long alfaTreatment;
    private Long inactiveTreatment;
    private LocalDate farStart;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        director = createUser(Role.OPERATIONS_DIRECTOR, "Paula Herrera");
        alfa = physiotherapist("Ana", "Alfa");
        beta = physiotherapist("Bruno", "Beta");
        zeta = physiotherapist("Zoe", "Zeta");
        partial = physiotherapist("Pía", "Parcial");
        inactive = physiotherapist("Iván", "Inactivo");

        TestStaff.insertSlots(jdbc, staffOf(alfa), "1 07:00 13:00", "3 07:00 13:00");
        TestStaff.insertSlots(jdbc, staffOf(zeta), "1 08:00 12:00");
        TestStaff.insertSlots(jdbc, staffOf(partial), "1 08:00 10:00");
        TestStaff.insertSlots(jdbc, staffOf(inactive), "1 07:00 20:00");
        // Beta's Monday arrives as two contiguous slots and is saved merged, 08:00-14:00 (adjustment of D9)
        betaStaff = staffOf(beta);
        mockMvc.perform(put("/api/staff/{id}/schedule", betaStaff).header(HttpHeaders.AUTHORIZATION, bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"slots":[{"day":"MONDAY","start":"08:00","end":"12:00"},
                                          {"day":"MONDAY","start":"12:00","end":"14:00"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1));

        // Loads: Alfa 2 active treatments (and a completed one, which does not count); the inactive one has 1
        // Five years ahead: before the ones that other tests need first (10 and 20 years ahead), after the rest
        farStart = LocalDate.now().plusYears(5).plusDays(Math.abs(suffix.hashCode() % 300));
        alfaTreatment = treatment("Rosa Elena", "Ortiz " + suffix, "ACTIVE", farStart, alfa);
        treatment("Luis", "Quishpe " + suffix, "ACTIVE", farStart.minusDays(1), alfa);
        treatment("Jorge", "Salazar " + suffix, "COMPLETED", farStart.minusDays(2), alfa);
        inactiveTreatment = treatment("Daniela", "Zambrano " + suffix, "ACTIVE", farStart.minusDays(3), inactive);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", inactive.getId());
    }

    @Test
    void theBoardListsEveryPhysiotherapistByLastNamesWithItsSlotsLoadAndStatus() throws Exception {
        String body = perform(get("/api/operations/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingTime").value("07:00"))
                .andExpect(jsonPath("$.closingTime").value("20:00"))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> mine = JsonPath.read(body, "$.physiotherapists[?(@.fullName =~ /.* " + suffix + "/)]");
        assertThat(mine).extracting(row -> row.get("physiotherapistId")).containsExactly(
                alfa.getId().intValue(), beta.getId().intValue(), inactive.getId().intValue(),
                partial.getId().intValue(), zeta.getId().intValue());
        assertThat(mine.get(0)).containsEntry("activeTreatments", 2).containsEntry("active", true)
                .containsEntry("fullName", "Ana Alfa " + suffix);
        assertThat(mine.get(1)).containsEntry("activeTreatments", 0).containsEntry("staffId", betaStaff.intValue());
        assertThat(mine.get(2)).containsEntry("active", false).containsEntry("activeTreatments", 1);
        assertThat(mine.get(0).get("slots")).isEqualTo(List.of(
                Map.of("day", "MONDAY", "start", "07:00", "end", "13:00"),
                Map.of("day", "WEDNESDAY", "start", "07:00", "end", "13:00")));
        assertThat(mine.get(1).get("slots")).isEqualTo(List.of(
                Map.of("day", "MONDAY", "start", "08:00", "end", "14:00")));
    }

    @Test
    void availabilityKeepsTheActiveOnesWhoseSlotCoversTheRangeByLoadAndThenLastNames() throws Exception {
        // 09:00-11:00 on Monday: Alfa (load 2), Beta and Zeta (load 0); Parcial ends at 10:00, Inactivo is inactive
        assertThat(available("MONDAY", "09:00", "11:00"))
                .containsExactly(beta.getId(), zeta.getId(), alfa.getId());
        // 11:00-13:00 crosses the former boundary of Beta's merged slots; Zeta ends at 12:00
        assertThat(available("MONDAY", "11:00", "13:00")).containsExactly(beta.getId(), alfa.getId());
        assertThat(available("WEDNESDAY", "07:00", "13:00")).containsExactly(alfa.getId());
        assertThat(available("SATURDAY", "08:00", "09:00")).isEmpty();

        perform(get("/api/operations/availability").param("day", "MONDAY").param("from", "09:00").param("to", "11:00"))
                .andExpect(jsonPath("$[?(@.physiotherapistId == %d)].activeTreatments".formatted(alfa.getId()))
                        .value(2))
                .andExpect(jsonPath("$[?(@.physiotherapistId == %d)].slots[0].end".formatted(alfa.getId()))
                        .value("13:00"))
                .andExpect(jsonPath("$[?(@.physiotherapistId == %d)].fullName".formatted(beta.getId()))
                        .value("Bruno Beta " + suffix));
    }

    @Test
    void anInvalidRangeAnswers400InSpanish() throws Exception {
        record Invalid(String day, String from, String to, String detail) {
        }
        for (Invalid invalid : List.of(
                new Invalid("SUNDAY", "09:00", "10:00", "La clínica atiende de lunes a sábado: elija otro día."),
                new Invalid("MONDAY", "10:00", "10:00", "La hora inicial debe ser anterior a la hora final."),
                new Invalid("MONDAY", "11:00", "10:00", "La hora inicial debe ser anterior a la hora final."),
                new Invalid("MONDAY", "06:00", "08:00",
                        "El rango debe estar dentro de la jornada de la clínica, de 07:00 a 20:00."))) {
            perform(get("/api/operations/availability").param("day", invalid.day()).param("from", invalid.from())
                    .param("to", invalid.to()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(invalid.detail()));
        }
        perform(get("/api/operations/availability").param("day", "MONDAY").param("from", "9am").param("to", "10:00"))
                .andExpect(status().isBadRequest());
        perform(get("/api/operations/availability").param("day", "LUNES").param("from", "09:00").param("to", "10:00"))
                .andExpect(status().isBadRequest());
        perform(get("/api/operations/availability").param("day", "MONDAY").param("from", "09:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTreatmentListShowsOnlyActiveTreatmentsWithoutClinicalDataAndFlagsTheInactiveResponsible()
            throws Exception {
        String body = perform(get("/api/operations/treatments").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> mine = JsonPath.read(body, "$.content[?(@.patientName =~ /.* " + suffix + "/)]");
        assertThat(mine).extracting(row -> ((Number) row.get("id")).longValue())
                .hasSize(3).contains(alfaTreatment, inactiveTreatment);
        assertThat(mine).allSatisfy(row -> assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(
                TREATMENT_PROPERTIES));
        Map<String, Object> first = mine.get(0);
        assertThat(first).containsEntry("id", alfaTreatment.intValue())
                .containsEntry("patientName", "Rosa Elena Ortiz " + suffix)
                .containsEntry("startDate", farStart.toString())
                .containsEntry("responsible", Map.of("id", alfa.getId().intValue(), "fullName", "Ana Alfa " + suffix))
                .containsEntry("responsibleInactive", false)
                .containsEntry("scheduleDays", List.of("MONDAY", "WEDNESDAY"));
        assertThat(mine.get(2)).containsEntry("id", inactiveTreatment.intValue())
                .containsEntry("responsibleInactive", true)
                .containsEntry("scheduleDays", List.of("MONDAY"));
        // Nothing clinical anywhere in the response
        assertThat(body).doesNotContain("M54.5", "Dolor lumbar", "diagnos", "Reducir el dolor", "painScore");

        // Each listed treatment is audited as READ, by the director
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action = 'READ' AND entity_type = 'Treatment' AND entity_id = ? AND user_id = ?""",
                Integer.class, alfaTreatment, director.getId())).isEqualTo(1);
    }

    @Test
    void theInactiveResponsibleFilterKeepsOnlyThoseTreatments() throws Exception {
        String body = perform(get("/api/operations/treatments").param("inactiveResponsible", "true")
                .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Number> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).extracting(Number::longValue).contains(inactiveTreatment).doesNotContain(alfaTreatment);
        List<Boolean> flags = JsonPath.read(body, "$.content[*].responsibleInactive");
        assertThat(flags).isNotEmpty().containsOnly(true);
    }

    @Test
    void theDirectorReassignsTheResponsibleWithAuditAndAnEnversRevision() throws Exception {
        reassign(inactiveTreatment, beta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactiveTreatment))
                .andExpect(jsonPath("$.patientName").value("Daniela Zambrano " + suffix))
                .andExpect(jsonPath("$.responsible.id").value(beta.getId()))
                .andExpect(jsonPath("$.responsible.fullName").value("Bruno Beta " + suffix))
                .andExpect(jsonPath("$.responsibleInactive").value(false))
                .andExpect(jsonPath("$.scheduleDays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.diagnosisCode").doesNotExist());

        assertThat(jdbc.queryForList("""
                SELECT physiotherapist_id FROM responsible_assignments WHERE treatment_id = ? AND valid_to IS NULL""",
                Long.class, inactiveTreatment)).containsExactly(beta.getId());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM responsible_assignments WHERE treatment_id = ? AND valid_to IS NOT NULL""",
                Integer.class, inactiveTreatment)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = 'UPDATE' AND entity_type = 'Treatment' AND entity_id = ?""",
                Long.class, inactiveTreatment)).containsExactly(director.getId());
        assertThat(jdbc.queryForList("""
                SELECT r.user_id FROM treatments_aud a JOIN revinfo r ON r.rev = a.rev
                WHERE a.id = ? AND a.revtype = 1""", Long.class, inactiveTreatment)).containsExactly(director.getId());
        assertThat(jdbc.queryForObject("SELECT updated_by FROM treatments WHERE id = ?", Long.class,
                inactiveTreatment)).isEqualTo(director.getId());

        // Choosing the current responsible changes and audits nothing
        reassign(inactiveTreatment, beta.getId()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'UPDATE' AND entity_type = 'Treatment' AND entity_id = ?""",
                Integer.class, inactiveTreatment)).isEqualTo(1);
    }

    @Test
    void theNewResponsibleMustBeAnActivePhysiotherapistOfAnActiveTreatment() throws Exception {
        for (Long invalid : List.of(inactive.getId(), director.getId(), 999_999_999L)) {
            reassign(alfaTreatment, invalid)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("El nuevo responsable debe ser un fisioterapeuta activo."));
        }
        Long completed = jdbc.queryForObject("""
                SELECT t.id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                JOIN patients p ON p.id = r.patient_id WHERE p.last_name = ?""", Long.class, "Salazar " + suffix);
        reassign(completed, beta.getId()).andExpect(status().isUnprocessableContent());
        reassign(999_999_999L, beta.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El tratamiento no existe."));
        perform(put("/api/operations/treatments/{id}/responsible", alfaTreatment)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("physiotherapistId"));

        assertThat(jdbc.queryForList("""
                SELECT physiotherapist_id FROM responsible_assignments WHERE treatment_id = ? AND valid_to IS NULL""",
                Long.class, alfaTreatment)).containsExactly(alfa.getId());
    }

    private List<Long> available(String day, String from, String to) throws Exception {
        String body = perform(get("/api/operations/availability").param("day", day).param("from", from)
                .param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.fullName =~ /.* " + suffix + "/)].physiotherapistId");
        return ids.stream().map(Number::longValue).toList();
    }

    private ResultActions reassign(Long treatmentId, Long physiotherapistId) throws Exception {
        return perform(put("/api/operations/treatments/{id}/responsible", treatmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"physiotherapistId\":%d}".formatted(physiotherapistId)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(director)));
    }

    private Long treatment(String firstName, String lastName, String status, LocalDate start, User responsible) {
        Long patientId = TestPatients.insert(jdbc, firstName, lastName);
        Long treatmentId = TestTreatments.insertFor(jdbc, TestTreatments.insertRecord(jdbc, patientId), status, start,
                6, 10);
        TestTreatments.assignResponsible(jdbc, treatmentId, responsible.getId());
        return treatmentId;
    }

    private User physiotherapist(String firstName, String lastName) {
        User user = createUser(Role.PHYSIOTHERAPIST, firstName + " " + lastName + " " + suffix);
        TestStaff.insertRecord(jdbc, user.getId(), firstName, lastName + " " + suffix);
        return user;
    }

    private Long staffOf(User user) {
        return jdbc.queryForObject("SELECT id FROM staff_records WHERE user_id = ?", Long.class, user.getId());
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("operaciones-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.issue(user, Instant.now()).token();
    }

}
