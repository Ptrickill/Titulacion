package ec.rehabelite.staff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestStaff;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * MEJ-04: weekly schedule of a physiotherapist. The administrator and the operations director read and replace it
 * (GET and PUT /api/staff/{id}/schedule), the physiotherapist reads its own (GET /api/physio/schedule). The rules of
 * the application (400 in Spanish) and of the database (V8) are checked here; the merging rule also in
 * WeeklyScheduleTests.
 */
@AutoConfigureMockMvc
class StaffScheduleIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User physio;
    private User director;
    private Long staffId;
    private String adminToken;
    private String directorToken;

    @BeforeEach
    void setUp() {
        physio = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade");
        staffId = TestStaff.insertRecord(jdbc, physio.getId(), "Valeria", "Andrade " + UUID.randomUUID());
        adminToken = tokenOf(createUser(Role.ADMINISTRATOR, "Admin Horarios"));
        director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Horarios");
        directorToken = tokenOf(director);
    }

    @Test
    void aNewPhysiotherapistHasNoSlotsAndTheClinicHours() throws Exception {
        mockMvc.perform(get("/api/staff/{id}/schedule", staffId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(staffId))
                .andExpect(jsonPath("$.openingTime").value("07:00"))
                .andExpect(jsonPath("$.closingTime").value("20:00"))
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void theDirectorReplacesTheWeekOrderedAndWithTheContiguousSlotsMerged() throws Exception {
        replace(directorToken, """
                [{"day":"WEDNESDAY","start":"14:00","end":"18:00"},
                 {"day":"MONDAY","start":"12:00","end":"14:00"},
                 {"day":"MONDAY","start":"08:00","end":"12:00"},
                 {"day":"SATURDAY","start":"07:00","end":"20:00"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.slots[0].start").value("08:00"))
                .andExpect(jsonPath("$.slots[0].end").value("14:00"))
                .andExpect(jsonPath("$.slots[1].day").value("WEDNESDAY"))
                .andExpect(jsonPath("$.slots[2].day").value("SATURDAY"))
                .andExpect(jsonPath("$.slots[2].end").value("20:00"));

        assertThat(storedSlots()).containsExactly("1 08:00:00-14:00:00", "3 14:00:00-18:00:00",
                "6 07:00:00-20:00:00");
        assertThat(audits("UPDATE")).hasSize(1);

        // The same week again (contiguous or already merged) changes and audits nothing
        replace(directorToken, """
                [{"day":"MONDAY","start":"08:00","end":"14:00"},
                 {"day":"WEDNESDAY","start":"14:00","end":"18:00"},
                 {"day":"SATURDAY","start":"07:00","end":"20:00"}]""").andExpect(status().isOk());
        assertThat(audits("UPDATE")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT created_at) FROM staff_schedule_slots "
                + "WHERE staff_record_id = ?", Integer.class, staffId)).isEqualTo(1);
    }

    @Test
    void theDirectorAlsoEditsAndAnEmptyWeekRemovesEverySlot() throws Exception {
        TestStaff.insertSlots(jdbc, staffId, "2 09:00 13:00");

        replace(directorToken, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(0));

        assertThat(storedSlots()).isEmpty();
        assertThat(audits("UPDATE")).containsExactly(director.getId());
    }

    @Test
    void replacingSlotsThatOverlapTheSavedOnesWorksBecauseTheOldOnesGoFirst() throws Exception {
        TestStaff.insertSlots(jdbc, staffId, "1 08:00 12:00");

        replace(directorToken, "[{\"day\":\"MONDAY\",\"start\":\"10:00\",\"end\":\"16:00\"}]")
                .andExpect(status().isOk());

        assertThat(storedSlots()).containsExactly("1 10:00:00-16:00:00");
        assertThat(jdbc.queryForObject("SELECT created_by FROM staff_schedule_slots WHERE staff_record_id = ?",
                Long.class, staffId)).isNotNull();
    }

    @Test
    void rejectsOverlappingSlotsOutsideTheClinicHoursAndOtherInvalidOnesInSpanish() throws Exception {
        TestStaff.insertSlots(jdbc, staffId, "1 08:00 12:00");
        record Invalid(String slots, String detail) {
        }
        for (Invalid invalid : List.of(
                new Invalid("""
                        [{"day":"TUESDAY","start":"08:00","end":"12:00"},
                         {"day":"TUESDAY","start":"11:00","end":"13:00"}]""",
                        "Las franjas del martes se superponen: 08:00–12:00 y 11:00–13:00."),
                new Invalid("""
                        [{"day":"FRIDAY","start":"09:00","end":"10:00"},
                         {"day":"FRIDAY","start":"09:00","end":"10:00"}]""",
                        "Las franjas del viernes se superponen: 09:00–10:00 y 09:00–10:00."),
                new Invalid("[{\"day\":\"MONDAY\",\"start\":\"06:30\",\"end\":\"09:00\"}]",
                        "La franja del lunes (06:30–09:00) está fuera de la jornada de la clínica, de 07:00 a 20:00."),
                new Invalid("[{\"day\":\"MONDAY\",\"start\":\"18:00\",\"end\":\"20:30\"}]",
                        "La franja del lunes (18:00–20:30) está fuera de la jornada de la clínica, de 07:00 a 20:00."),
                new Invalid("[{\"day\":\"THURSDAY\",\"start\":\"12:00\",\"end\":\"12:00\"}]",
                        "En la franja del jueves (12:00–12:00), la hora de inicio debe ser anterior a la de fin."),
                new Invalid("[{\"day\":\"THURSDAY\",\"start\":\"15:00\",\"end\":\"09:00\"}]",
                        "En la franja del jueves (15:00–09:00), la hora de inicio debe ser anterior a la de fin."),
                new Invalid("[{\"day\":\"SUNDAY\",\"start\":\"08:00\",\"end\":\"10:00\"}]",
                        "La clínica atiende de lunes a sábado: el domingo no puede tener franjas."),
                new Invalid("[{\"day\":\"MONDAY\",\"start\":\"08:00:30\",\"end\":\"10:00\"}]",
                        "Las horas de las franjas van en horas y minutos, sin segundos."))) {
            replace(directorToken, invalid.slots())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(invalid.detail()));
        }

        replace(directorToken, "[{\"day\":\"MONDAY\",\"start\":\"08:00\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slots[0].end"));
        putBody(directorToken, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slots"));

        // Nothing changed and nothing was audited
        assertThat(storedSlots()).containsExactly("1 08:00:00-12:00:00");
        assertThat(audits("UPDATE")).isEmpty();
    }

    @Test
    void onlyAPhysiotherapistsRecordHasASchedule() throws Exception {
        User admin = createUser(Role.ADMINISTRATOR, "Otra Administradora");
        Long adminStaffId = TestStaff.insertRecord(jdbc, admin.getId(), "Otra", "Administradora " + UUID.randomUUID());

        for (ResultActions notPhysio : List.of(
                mockMvc.perform(get("/api/staff/{id}/schedule", adminStaffId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))),
                mockMvc.perform(put("/api/staff/{id}/schedule", adminStaffId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}")))) {
            notPhysio.andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Solo los fisioterapeutas tienen horario."));
        }
        mockMvc.perform(get("/api/staff/{id}/schedule", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El registro del personal no existe."));
    }

    @Test
    void thePhysiotherapistReadsItsOwnScheduleButCannotEditIt() throws Exception {
        TestStaff.insertSlots(jdbc, staffId, "4 13:00 19:00", "2 13:00 19:00");
        String physioToken = tokenOf(physio);

        mockMvc.perform(get("/api/physio/schedule").header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(staffId))
                .andExpect(jsonPath("$.fullName").value(startsWith("Valeria Andrade")))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].day").value("TUESDAY"))
                .andExpect(jsonPath("$.slots[1].day").value("THURSDAY"));
        replace(physioToken, "[]").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/{id}/schedule", staffId).header(HttpHeaders.AUTHORIZATION, bearer(physioToken)))
                .andExpect(status().isForbidden());

        // An account without a staff record (not possible since MEJ-02, but kept safe)
        User withoutRecord = createUser(Role.PHYSIOTHERAPIST, "Sin Registro");
        mockMvc.perform(get("/api/physio/schedule").header(HttpHeaders.AUTHORIZATION, bearer(tokenOf(withoutRecord))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Su cuenta no tiene un registro del personal."));
        assertThat(storedSlots()).hasSize(2);
    }

    /** MEJ-05: the reception reads the schedules (it will book appointments with them) but never edits them. */
    @Test
    void theReceptionReadsASchedulesButCannotEditIt() throws Exception {
        TestStaff.insertSlots(jdbc, staffId, "1 08:00 12:00");

        mockMvc.perform(get("/api/staff/{id}/schedule", staffId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1));
        replace(adminToken, "[]")
                .andExpect(status().isForbidden());

        assertThat(storedSlots()).containsExactly("1 08:00:00-12:00:00");
        assertThat(audits("UPDATE")).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                WHERE action = 'ACCESS_DENIED' AND entity_type = 'staff' AND entity_id = ?""", Integer.class, staffId))
                .isEqualTo(1);
    }

    @Test
    void thePatientNeverReachesASchedule() throws Exception {
        String patientToken = tokenOf(createUser(Role.PATIENT, "Paciente Horario"));

        mockMvc.perform(get("/api/physio/schedule").header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/{id}/schedule", staffId).header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isForbidden());
        replace(patientToken, "[]").andExpect(status().isForbidden());
    }

    /**
     * Two replacements at the same time run one after the other (the record is locked): both succeed and the week is
     * exactly one of them, never a mix with overlapping slots.
     */
    @Test
    void concurrentReplacementsRunOneAfterTheOther() throws Exception {
        String first = "[{\"day\":\"MONDAY\",\"start\":\"08:00\",\"end\":\"12:00\"},"
                + "{\"day\":\"TUESDAY\",\"start\":\"08:00\",\"end\":\"12:00\"}]";
        String second = "[{\"day\":\"MONDAY\",\"start\":\"10:00\",\"end\":\"16:00\"}]";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String week : List.of(first, second)) {
                Callable<Integer> call = () -> {
                    start.await();
                    return replace(directorToken, week).andReturn().getResponse().getStatus();
                };
                results.add(executor.submit(call));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(storedSlots()).isIn(List.of("1 08:00:00-12:00:00", "2 08:00:00-12:00:00"),
                List.of("1 10:00:00-16:00:00"));
        assertThat(audits("UPDATE")).hasSize(2);
    }

    /** V8 as the safety net: overlapping slots of the same day are rejected, contiguous ones and other days are not. */
    @Test
    void theDatabaseRejectsOverlappingSlotsAndHoursOutsideTheClinicDay() {
        TestStaff.insertSlots(jdbc, staffId, "1 08:00 12:00", "1 12:00 14:00", "2 10:00 11:00");

        assertThatThrownBy(() -> TestStaff.insertSlots(jdbc, staffId, "1 11:00 13:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_staff_schedule_slots_overlap");
        for (String invalid : List.of("3 06:59 09:00", "3 19:00 20:01", "3 10:00 09:00", "7 10:00 11:00")) {
            assertThatThrownBy(() -> TestStaff.insertSlots(jdbc, staffId, invalid))
                    .as(invalid).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_staff_schedule_slots");
        }
        // Another physiotherapist may work at the same time
        User other = createUser(Role.PHYSIOTHERAPIST, "Martín Cevallos");
        Long otherStaff = TestStaff.insertRecord(jdbc, other.getId(), "Martín", "Cevallos " + UUID.randomUUID());
        TestStaff.insertSlots(jdbc, otherStaff, "1 08:00 12:00");
        assertThat(storedSlots()).hasSize(3);
    }

    private ResultActions replace(String token, String slots) throws Exception {
        return putBody(token, "{\"slots\":" + slots + "}");
    }

    private ResultActions putBody(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/staff/{id}/schedule", staffId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<String> storedSlots() {
        return jdbc.queryForList("""
                SELECT day_of_week || ' ' || start_time || '-' || end_time FROM staff_schedule_slots
                WHERE staff_record_id = ? ORDER BY day_of_week, start_time""", String.class, staffId);
    }

    private List<Long> audits(String action) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = ? AND entity_type = 'StaffRecord' AND entity_id = ?""",
                Long.class, action, staffId);
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("horario-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
