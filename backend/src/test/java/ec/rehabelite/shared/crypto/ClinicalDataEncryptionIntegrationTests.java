package ec.rehabelite.shared.crypto;

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
import ec.rehabelite.TestClinicalText;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChange;
import ec.rehabelite.sessions.domain.TherapySession;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
 * MEJ-06 (B4): the clinical free text is stored encrypted, also in the Envers tables, while the API keeps answering
 * the text. Every encrypted entity field is in the list that V11 and the dev seed encrypt.
 */
@AutoConfigureMockMvc
class ClinicalDataEncryptionIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theColumnsNeverHoldThePlainTextAndTheApiAnswersIt() throws Exception {
        User physio = userRepository.save(User.create("cifrado-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Fisio del cifrado", Role.PHYSIOTHERAPIST, null, Instant.now()));
        Long patientId = TestPatients.insert(jdbc, "Paciente", "Cifrado");
        perform(physio, post("/api/patients/{id}/clinical-record", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(TestClinicalRecords.record("Fuma 2 cigarrillos al día"))).andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class,
                patientId);
        // The treatment started a month ago, so a session of yesterday is inside it
        jdbc.update("UPDATE treatments SET start_date = start_date - 30 WHERE clinical_record_id = ?", recordId);
        Long treatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
        String session = perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(5)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long sessionId = ((Number) JsonPath.read(session, "$.id")).longValue();
        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                .content(TestSessions.body(LocalDate.now().minusDays(1), 3, "Ejercicios de Williams", "")))
                .andExpect(status().isOk());
        perform(physio, patch("/api/treatments/{id}/status", treatmentId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"ABANDONED\",\"reason\":\"No asiste desde hace un mes\"}"))
                .andExpect(status().isOk());

        Map<String, String> plainTexts = Map.of(
                "clinical_records.habits", "Fuma 2 cigarrillos al día",
                "treatments.chief_complaint", "Dolor lumbar al levantar peso",
                "treatments_aud.chief_complaint", "Dolor lumbar al levantar peso",
                "treatment_status_changes.reason", "No asiste desde hace un mes",
                "therapy_sessions.activities_performed", "Ejercicios de Williams",
                "therapy_sessions.clinical_observations", "Buena tolerancia al ejercicio",
                "therapy_sessions_aud.activities_performed", "Movilización lumbar y TENS 15 minutos",
                "therapy_sessions_aud.patient_instructions", "Calor local 15 minutos en casa");
        Map<String, String> where = Map.of(
                "clinical_records", "id = " + recordId,
                "treatments", "id = " + treatmentId,
                "treatments_aud", "id = " + treatmentId,
                "treatment_status_changes", "treatment_id = " + treatmentId,
                "therapy_sessions", "id = " + sessionId,
                "therapy_sessions_aud", "id = " + sessionId);
        for (ClinicalTextColumns.Table table : ClinicalTextColumns.ALL) {
            if (!where.containsKey(table.name())) {
                continue;
            }
            for (String column : table.columns()) {
                List<String> values = jdbc.queryForList("SELECT " + column + " FROM " + table.name() + " WHERE "
                        + where.get(table.name()), String.class);
                assertThat(values).as(table.name() + "." + column).isNotEmpty()
                        .allSatisfy(value -> assertThat(value).startsWith("enc:v1:"));
                String plain = plainTexts.get(table.name() + "." + column);
                if (plain != null) {
                    assertThat(String.join(" ", values)).doesNotContain(plain);
                    assertThat(values.stream().map(TestClinicalText::decrypt)).contains(plain);
                }
            }
        }

        perform(physio, get("/api/patients/{id}/clinical-record", patientId))
                .andExpect(jsonPath("$.clinicalRecord.habits").value("Fuma 2 cigarrillos al día"));
        perform(physio, get("/api/therapy-sessions/{id}/versions", sessionId))
                .andExpect(jsonPath("$[1].session.activitiesPerformed").value("Movilización lumbar y TENS 15 minutos"));
    }

    @Test
    void everyEncryptedFieldOfTheEntitiesIsInTheListOfEncryptedColumns() {
        Map<String, List<String>> fromEntities = new TreeMap<>();
        for (Class<?> entity : List.of(ClinicalRecord.class, Treatment.class, TreatmentStatusChange.class,
                TherapySession.class)) {
            String table = entity.getAnnotation(Table.class).name();
            List<String> columns = new ArrayList<>();
            for (Field field : entity.getDeclaredFields()) {
                Convert convert = field.getAnnotation(Convert.class);
                if (convert != null && convert.converter() == EncryptedText.class) {
                    String name = field.getAnnotation(Column.class).name();
                    columns.add(name.isEmpty() ? field.getName() : name);
                }
            }
            fromEntities.put(table, columns);
        }

        Map<String, List<String>> listed = new TreeMap<>();
        ClinicalTextColumns.ALL.stream().filter(table -> fromEntities.containsKey(table.name()))
                .forEach(table -> listed.put(table.name(), table.columns()));
        assertThat(listed).isEqualTo(fromEntities);
        // The versioned entities have their _aud table listed with the same columns; ai_summaries has no entity yet
        assertThat(ClinicalTextColumns.ALL.stream().map(ClinicalTextColumns.Table::name))
                .contains("treatments_aud", "therapy_sessions_aud", "ai_summaries");
        assertThat(columnsOf("treatments_aud")).isEqualTo(fromEntities.get("treatments"));
        assertThat(columnsOf("therapy_sessions_aud")).isEqualTo(fromEntities.get("therapy_sessions"));
        assertThat(Arrays.stream(new String[] {"generated_content", "final_content"}).toList())
                .isEqualTo(columnsOf("ai_summaries"));
    }

    private static List<String> columnsOf(String table) {
        return ClinicalTextColumns.ALL.stream().filter(t -> t.name().equals(table)).findFirst().orElseThrow()
                .columns();
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

}
