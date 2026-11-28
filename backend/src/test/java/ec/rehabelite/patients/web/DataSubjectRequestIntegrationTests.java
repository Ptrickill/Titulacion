package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalRecords;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.ClinicCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * MEJ-06 (B3, D19, D20): the reception registers the requests of the data subject and follows them with their due
 * date; only the operations director accepts an erasure, confirming it with the national ID. Accepting it blocks the
 * patient and clears its contact data, its consents and its portal account, and keeps the clinical record.
 */
@AutoConfigureMockMvc
class DataSubjectRequestIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private User reception;
    private User director;
    private User physio;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        reception = createUser(Role.ADMINISTRATOR);
        director = createUser(Role.OPERATIONS_DIRECTOR);
        physio = createUser(Role.PHYSIOTHERAPIST);
        today = ClinicCalendar.today(clock);
    }

    @Test
    void theReceptionRegistersARequestAndClosesItWithAResponseInsideItsDueDate() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Rosa", "Ortiz");

        Long requestId = register(patientId, "ACCESS", today.minusDays(4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.dueOn").value(today.minusDays(4).plusDays(15).toString()))
                .andExpect(jsonPath("$.daysElapsed").value(4))
                .andExpect(jsonPath("$.overdue").value(false))
                .andReturn().getResponse().getContentAsString().transform(this::idOf);

        change(patientId, requestId, "IN_PROGRESS", null).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        change(patientId, requestId, "RESOLVED", " ").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Escriba la respuesta al titular para cerrar la solicitud."));
        change(patientId, requestId, "RESOLVED", "Se entregó la exportación de sus datos.")
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.response").value("Se entregó la exportación de sus datos."))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        change(patientId, requestId, "REJECTED", "Otra respuesta").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La solicitud ya está cerrada."));

        perform(reception, get("/api/patients/{id}/data-requests", patientId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ACCESS"));
        assertThat(jdbc.queryForList("""
                SELECT action FROM audit_logs WHERE entity_type = 'DataSubjectRequest' AND entity_id = ? ORDER BY id""",
                String.class, requestId)).containsExactly("CREATE", "STATUS_CHANGE", "STATUS_CHANGE");
    }

    @Test
    void anOpenRequestPastItsDueDateIsOverdueAndAFutureDateIsRejected() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Luis", "Quishpe");
        register(patientId, "RECTIFICATION", today.minusDays(20))
                .andExpect(jsonPath("$.daysElapsed").value(20))
                .andExpect(jsonPath("$.overdue").value(true));
        register(patientId, "PORTABILITY", today.plusDays(1)).andExpect(status().isBadRequest());
        register(999_999_999L, "ACCESS", today).andExpect(status().isNotFound());
    }

    @Test
    void theReceptionNeverAcceptsAnErasureAndTheDirectorConfirmsItWithTheNationalId() throws Exception {
        Erasure erasure = patientWithRecordAndPortal();
        Long requestId = register(erasure.patientId(), "ERASURE", today).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::idOf);

        change(erasure.patientId(), requestId, "RESOLVED", "Aceptada").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Solo la dirección operativa puede aceptar una solicitud de eliminación."));
        perform(reception, get("/api/data-requests/erasures")).andExpect(status().isForbidden());

        perform(director, get("/api/data-requests/erasures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.request.id == %d)].nationalId".formatted(requestId))
                        .value(erasure.nationalId()))
                .andExpect(jsonPath("$[?(@.request.id == %d)].patientName".formatted(requestId))
                        .value("Paciente Por Eliminar"));
        accept(requestId, "1710034065", "Aceptada").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La cédula ingresada no coincide con la del paciente."));
        accept(requestId, "123", "Aceptada").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT blocked_at IS NULL FROM patients WHERE id = ?", Boolean.class,
                erasure.patientId())).isTrue();

        accept(requestId, erasure.nationalId(), "Se bloquearon sus datos; la historia clínica se conserva.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Map<String, Object> patient = jdbc.queryForMap("""
                SELECT email, phone, province_code, main_street, address_reference, blocked_at, blocked_by,
                       first_name, national_id, medical_record_number, birth_date
                FROM patients WHERE id = ?""", erasure.patientId());
        assertThat(patient).containsEntry("email", null).containsEntry("phone", null)
                .containsEntry("province_code", null).containsEntry("main_street", null)
                .containsEntry("address_reference", null).containsEntry("blocked_by", director.getId())
                .containsEntry("first_name", "Paciente").containsEntry("national_id", erasure.nationalId())
                .containsEntry("medical_record_number", erasure.nationalId());
        assertThat(patient.get("blocked_at")).isNotNull();
        assertThat(patient.get("birth_date")).isNotNull();
        User account = userRepository.findById(erasure.userId()).orElseThrow();
        assertThat(account.isActive()).isFalse();
        assertThat(account.getEmail()).isEqualTo("borrado+" + erasure.userId() + "@invalid.rehabelite");
        assertThat(jdbc.queryForList("""
                SELECT revocation_channel FROM patient_consents WHERE patient_id = ?""", String.class,
                erasure.patientId())).containsOnly("ERASURE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'ANONYMIZATION'
                AND ((entity_type = 'Patient' AND entity_id = ?) OR (entity_type = 'User' AND entity_id = ?))""",
                Integer.class, director.getId(), erasure.patientId(), erasure.userId())).isEqualTo(2);

        // Blocked: nobody edits it nor gives it new care, and the record stays readable for the physiotherapist
        perform(reception, get("/api/patients/{id}", erasure.patientId()))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
        perform(reception, put("/api/patients/{id}", erasure.patientId()).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Los datos del paciente están bloqueados por una solicitud de eliminación."));
        perform(physio, post("/api/treatments/{id}/therapy-sessions", erasure.treatmentId())
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                .andExpect(status().isUnprocessableContent());
        perform(physio, get("/api/patients/{id}/clinical-record", erasure.patientId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalRecord.habits").value("No fuma"));
        perform(reception, post("/api/patients/{id}/consents", erasure.patientId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DATA_PROCESSING\",\"version\":\"%s\"}"
                                .formatted(TestPatients.DATA_PROCESSING_VERSION)))
                .andExpect(status().isUnprocessableContent());
        accept(requestId, erasure.nationalId(), "Otra vez").andExpect(status().isUnprocessableContent());
    }

    @Test
    void theDirectorMayRejectAnErasureAndOnlyErasuresGoThroughItsEndpoints() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Ana", "Mendoza");
        Long erasureId = register(patientId, "ERASURE", today).andReturn().getResponse().getContentAsString()
                .transform(this::idOf);
        Long accessId = register(patientId, "ACCESS", today).andReturn().getResponse().getContentAsString()
                .transform(this::idOf);

        perform(director, post("/api/data-requests/erasures/{id}/rejection", accessId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"No aplica\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La solicitud no es de eliminación."));
        perform(director, post("/api/data-requests/erasures/{id}/rejection", erasureId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Existe una obligación legal de conservar los datos.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT blocked_at IS NULL FROM patients WHERE id = ?", Boolean.class,
                patientId)).isTrue();
        perform(reception, get("/api/patients/{id}/data-requests", patientId)).andExpect(status().isOk());
        perform(director, get("/api/patients/{id}/data-requests", patientId)).andExpect(status().isForbidden());
    }

    private record Erasure(Long patientId, String nationalId, Long treatmentId, Long userId) {
    }

    private Erasure patientWithRecordAndPortal() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "Paciente", "Por Eliminar");
        jdbc.update("UPDATE patients SET email = ? WHERE id = ?", "eliminar-" + UUID.randomUUID() + "@rehabelite.test",
                patientId);
        TestPatients.grantConsent(jdbc, patientId, "EMAIL_COMMUNICATIONS");
        perform(physio, post("/api/patients/{id}/clinical-record", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(TestClinicalRecords.record("No fuma"))).andExpect(status().isCreated());
        Long treatmentId = jdbc.queryForObject("""
                SELECT t.id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                WHERE r.patient_id = ?""", Long.class, patientId);
        String body = perform(reception, post("/api/patients/{id}/portal-account", patientId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();
        return new Erasure(patientId, TestPatients.nationalIdOf(jdbc, patientId), treatmentId, userId);
    }

    private ResultActions register(Long patientId, String type, LocalDate receivedOn) throws Exception {
        return perform(reception, post("/api/patients/{id}/data-requests", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"receivedOn\":\"%s\"}".formatted(type, receivedOn)));
    }

    private ResultActions change(Long patientId, Long requestId, String status, String response) throws Exception {
        String body = response == null ? "{\"status\":\"%s\"}".formatted(status)
                : "{\"status\":\"%s\",\"response\":\"%s\"}".formatted(status, response);
        return perform(reception, patch("/api/patients/{id}/data-requests/{requestId}", patientId, requestId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions accept(Long requestId, String nationalId, String response) throws Exception {
        return perform(director, post("/api/data-requests/erasures/{id}/acceptance", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nationalId\":\"%s\",\"response\":\"%s\"}".formatted(nationalId, response)));
    }

    private Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("solicitudes-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de solicitudes", role, null, Instant.now()));
    }

    private static String updateBody() {
        return """
                {"admissionDate":"2026-01-15","firstName":"Paciente","lastName":"Por Eliminar",
                 "birthDate":"1985-04-12","sex":"FEMALE","phone":"0991112233","email":"nuevo@rehabelite.test",
                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Av. Ilaló",
                            "crossStreet":"Río Pita","reference":"Frente al parque"},
                 "occupation":"Contadora","educationLevel":"Superior","hasHealthInsurance":true}""";
    }

}
