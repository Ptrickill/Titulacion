package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-06: PUT /api/patients/{id} replaces the admission date and the personal data; the national ID and the medical
 * record number never change, and every real change records who and when and audits UPDATE.
 */
@AutoConfigureMockMvc
class PatientUpdateIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private String adminToken;
    private Long patientId;
    private String nationalId;

    @BeforeEach
    void setUp() {
        admin = createUser(Role.ADMINISTRATOR);
        adminToken = tokenOf(admin);
        patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        nationalId = TestPatients.nationalIdOf(jdbc, patientId);
    }

    @Test
    void updatesPersonalDataRecordsWhoAndWhenAndAuditsIt() throws Exception {
        update(adminToken, patientId, body(Map.of("phone", "0991112233", "religion", "Católica")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patientId))
                .andExpect(jsonPath("$.firstName").value("Rosa Elena"))
                .andExpect(jsonPath("$.lastName").value("Ortiz Vega"))
                .andExpect(jsonPath("$.phone").value("0991112233"))
                .andExpect(jsonPath("$.address.formatted").value("Av. 6 de Diciembre N34-12 y Av. Portugal, Iñaquito, "
                        + "Distrito Metropolitano de Quito, Pichincha. Referencia: Edificio azul"))
                .andExpect(jsonPath("$.address.complete").value(true))
                .andExpect(jsonPath("$.hasHealthInsurance").value(true))
                .andExpect(jsonPath("$.needsCompletion").value(false))
                .andExpect(jsonPath("$.religion").value("Católica"))
                .andExpect(jsonPath("$.admissionDate").value("2026-01-15"))
                .andExpect(jsonPath("$.age").isNumber())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.nationalId").value(nationalId));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT phone, parish_code, address_reference, admission_date, updated_by, updated_at, national_id,
                       medical_record_number
                FROM patients WHERE id = ?""", patientId);
        assertThat(row).containsEntry("phone", "0991112233")
                .containsEntry("parish_code", "170112")
                .containsEntry("address_reference", "Edificio azul")
                .containsEntry("updated_by", admin.getId())
                .containsEntry("national_id", nationalId)
                .containsEntry("medical_record_number", nationalId);
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("admission_date").toString()).isEqualTo("2026-01-15");
        assertThat(auditAuthors(patientId)).containsExactly(admin.getId());
    }

    @Test
    void nationalIdAndMedicalRecordNumberCannotBeChanged() throws Exception {
        String otherNationalId = TestPatients.randomNationalId();

        update(adminToken, patientId, body(Map.of("nationalId", otherNationalId, "medicalRecordNumber", "HC-999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nationalId").value(nationalId))
                .andExpect(jsonPath("$.medicalRecordNumber").value(nationalId));

        assertThat(TestPatients.nationalIdOf(jdbc, patientId)).isEqualTo(nationalId);
        assertThat(jdbc.queryForObject("SELECT medical_record_number FROM patients WHERE id = ?", String.class,
                patientId)).isEqualTo(nationalId);
    }

    @Test
    void sendingTheSameDataAgainIsNotAudited() throws Exception {
        String body = body(Map.of());
        update(adminToken, patientId, body).andExpect(status().isOk());
        Object firstUpdate = jdbc.queryForObject("SELECT updated_at FROM patients WHERE id = ?", Object.class,
                patientId);

        User otherAdmin = createUser(Role.ADMINISTRATOR);
        update(tokenOf(otherAdmin), patientId, body).andExpect(status().isOk());

        assertThat(auditAuthors(patientId)).containsExactly(admin.getId());
        assertThat(jdbc.queryForObject("SELECT updated_at FROM patients WHERE id = ?", Object.class, patientId))
                .isEqualTo(firstUpdate);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM patients WHERE id = ?", Long.class, patientId))
                .isEqualTo(admin.getId());
    }

    @Test
    void invalidDataIsRejectedWithoutChanges() throws Exception {
        update(adminToken, patientId, body(Map.of("admissionDate", "1980-01-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "La fecha de nacimiento no puede ser posterior a la fecha de ingreso."));
        update(adminToken, patientId, body(Map.of("email", "no-es-correo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        update(adminToken, patientId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(11));
        update(adminToken, 999_999_999L, body(Map.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));

        assertThat(auditAuthors(patientId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT updated_at FROM patients WHERE id = ?", Object.class, patientId))
                .isNull();
    }

    @Test
    void onlyTheAdministratorUpdatesPatients() throws Exception {
        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role);
            update(tokenOf(user), patientId, body(Map.of("phone", "0999999999")))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForList("""
                    SELECT entity_id FROM audit_logs WHERE user_id = ? AND action = 'ACCESS_DENIED'""",
                    Long.class, user.getId())).containsExactly(patientId);
        }
        mockMvc.perform(put("/api/patients/{id}", patientId).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of())))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT phone FROM patients WHERE id = ?", String.class, patientId))
                .isEqualTo("0990000000");
        assertThat(auditAuthors(patientId)).isEmpty();
    }

    /** Valid update with the patient's names; {@code overrides} replace or add fields (strings only). */
    private static String body(Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("admissionDate", "2026-01-15"),
                Map.entry("firstName", "Rosa Elena"),
                Map.entry("lastName", "Ortiz Vega"),
                Map.entry("birthDate", "1985-04-12"),
                Map.entry("sex", "FEMALE"),
                Map.entry("phone", "0990000000"),
                Map.entry("email", "rosa.ortiz@correo.ec"),
                Map.entry("address", """
                        {"provinceCode":"17","cantonCode":"1701","parishCode":"170112",\
                        "mainStreet":"Av. 6 de Diciembre","streetNumber":"N34-12","crossStreet":"Av. Portugal",\
                        "reference":"Edificio azul"}"""),
                Map.entry("occupation", "Contadora"),
                Map.entry("educationLevel", "Superior"),
                Map.entry("hasHealthInsurance", "true")));
        fields.putAll(overrides);
        // Objects and booleans go as they are; everything else as a JSON string
        return fields.entrySet().stream()
                .map(e -> e.getValue().startsWith("{") || e.getValue().equals("true") || e.getValue().equals("false")
                        ? "\"%s\":%s".formatted(e.getKey(), e.getValue())
                        : "\"%s\":\"%s\"".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private ResultActions update(String token, Long id, String body) throws Exception {
        return mockMvc.perform(put("/api/patients/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<Long> auditAuthors(Long id) {
        return jdbc.queryForList("""
                SELECT user_id FROM audit_logs WHERE action = 'UPDATE' AND entity_type = 'Patient' AND entity_id = ?
                ORDER BY id""", Long.class, id);
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("edicion-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
    }

    private String tokenOf(User user) {
        return tokenService.issue(user, Instant.now()).token();
    }

}
