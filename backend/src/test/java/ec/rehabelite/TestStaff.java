package ec.rehabelite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Staff members for integration tests (MEJ-02). Since MEJ-02 a staff account is created only by registering its
 * person ({@code POST /api/staff}), which also emails the activation link to {@link RecordingMailSender}. All the data
 * is synthetic: random national IDs with a valid check digit and a fixed address of the INEC catalog.
 */
public final class TestStaff {

    private TestStaff() {
    }

    /** Request body of POST /api/staff. */
    public static String json(String role, String nationalId, String firstName, String lastName, String email) {
        return """
                {"role":"%s","nationalId":"%s","firstName":"%s","lastName":"%s","birthDate":"1990-06-21",
                 "sex":"FEMALE","phone":"0987654321","email":"%s",
                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Av. Ilaló",
                            "streetNumber":"N12-34","crossStreet":"Río Pita","reference":"Frente al parque"}}"""
                .formatted(role, nationalId, firstName, lastName, email);
    }

    public static ResultActions register(MockMvc mockMvc, String adminToken, String body) throws Exception {
        return mockMvc.perform(post("/api/staff").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * The staff record of an existing account, inserted with SQL and without audit entries, for tests that only need
     * it to exist (MEJ-04: a physiotherapist's schedule); returns its id. The names must be unique to sort by them.
     */
    public static Long insertRecord(JdbcTemplate jdbc, Long userId, String firstName, String lastName) {
        return jdbc.queryForObject("""
                INSERT INTO staff_records (user_id, first_name, last_name, email)
                SELECT id, ?, ?, email FROM users WHERE id = ? RETURNING id""", Long.class, firstName, lastName,
                userId);
    }

    /** Slots of a schedule, inserted with SQL; each slot is "day start end" with the ISO day, e.g. "1 08:00 12:00". */
    public static void insertSlots(JdbcTemplate jdbc, Long staffId, String... slots) {
        for (String slot : slots) {
            String[] parts = slot.split(" ");
            jdbc.update("""
                    INSERT INTO staff_schedule_slots (staff_record_id, day_of_week, start_time, end_time)
                    VALUES (?, ?, CAST(? AS time), CAST(? AS time))""", staffId, Integer.parseInt(parts[0]), parts[1],
                    parts[2]);
        }
    }

    /** Registers a staff member and returns the id of its new account (pending activation). */
    public static Long registerAccount(MockMvc mockMvc, String adminToken, String role, String email)
            throws Exception {
        String body = register(mockMvc, adminToken,
                json(role, TestPatients.randomNationalId(), "Cuenta", "Nueva Prueba", email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.account.userId")).longValue();
    }

}
