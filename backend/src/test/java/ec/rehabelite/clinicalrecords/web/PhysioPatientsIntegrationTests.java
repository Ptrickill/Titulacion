package ec.rehabelite.clinicalrecords.web;

import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.recordBody;
import static ec.rehabelite.clinicalrecords.web.ClinicalRecordIntegrationTests.treatmentBody;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * HU-07: the ICD-10 catalog search (GET /api/icd10-diagnoses) and the physiotherapist's patient list with the
 * clinical summary of each patient (GET /api/physio/patients). Patients carry a unique word in their last name
 * ({@link #tag}) because the database is shared with other tests.
 */
@AutoConfigureMockMvc
class PhysioPatientsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private String physioToken;
    private String tag;

    @BeforeEach
    void setUp() {
        physioToken = tokenOf(Role.PHYSIOTHERAPIST);
        tag = "Zq" + ThreadLocalRandom.current().ints(8, 'a', 'z' + 1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
    }

    @Test
    void searchesTheCatalogByCodeOrDescriptionIgnoringAccents() throws Exception {
        diagnoses("ciatica", physioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("M54.4")))
                .andExpect(jsonPath("$[0].description").value("Lumbago con ciática"));
        diagnoses("m54", physioToken)
                .andExpect(jsonPath("$[*].code").value(everyItem(startsWith("M54"))))
                .andExpect(jsonPath("$.length()").value(3));
        diagnoses("TUNEL CARPIANO", physioToken)
                .andExpect(jsonPath("$[0].code").value("G56.0"));
        diagnoses("%", physioToken).andExpect(status().isOk());
        diagnoses("", physioToken).andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void listsPatientsWithTheSummaryOfTheirCurrentTreatment() throws Exception {
        Long withRecord = TestPatients.insert(jdbc, "Ana", tag + " Benítez");
        Long withoutRecord = TestPatients.insert(jdbc, "Luis", tag + " Moreira");
        mockMvc.perform(post("/api/patients/{id}/clinical-record", withRecord)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(treatmentBody(6, 12, "M75.1"))))
                .andExpect(status().isCreated());

        patients(tag, physioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(withRecord))
                .andExpect(jsonPath("$.content[0].age").isNumber())
                .andExpect(jsonPath("$.content[0].clinicalRecordId").isNumber())
                .andExpect(jsonPath("$.content[0].currentTreatment.diagnosisCode").value("M75.1"))
                .andExpect(jsonPath("$.content[0].currentTreatment.diagnosisDescription")
                        .value("Síndrome del manguito rotatorio"))
                .andExpect(jsonPath("$.content[0].currentTreatment.severity").value("MODERATE"))
                .andExpect(jsonPath("$.content[0].currentTreatment.plannedSessions").value(12))
                .andExpect(jsonPath("$.content[0].currentTreatment.status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].id").value(withoutRecord))
                .andExpect(jsonPath("$.content[1].clinicalRecordId").doesNotExist())
                .andExpect(jsonPath("$.content[1].currentTreatment").doesNotExist());
    }

    @Test
    void withoutAnActiveTreatmentTheMostRecentOneIsShown() throws Exception {
        Long patient = TestPatients.insert(jdbc, "Elena", tag);
        mockMvc.perform(post("/api/patients/{id}/clinical-record", patient)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(treatmentBody(6, 12, "M75.1"))))
                .andExpect(status().isCreated());
        jdbc.update("""
                UPDATE treatments SET status = 'ABANDONED'
                WHERE clinical_record_id = (SELECT id FROM clinical_records WHERE patient_id = ?)""", patient);

        patients(tag, physioToken)
                .andExpect(jsonPath("$.content[0].currentTreatment.status").value("ABANDONED"));
    }

    @Test
    void searchesLikeThePatientRegistryAndPaginates() throws Exception {
        for (String surname : List.of("Cárdenas", "Álvarez", "Benítez")) {
            TestPatients.insert(jdbc, "Ana", tag + " " + surname);
        }

        mockMvc.perform(get("/api/physio/patients").param("q", tag.toLowerCase() + " alvarez")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value(tag + " Álvarez"));
        mockMvc.perform(get("/api/physio/patients").param("q", tag).param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value(tag + " Cárdenas"));
    }

    @Test
    void onlyPhysiotherapistsUseTheCatalogAndTheList() throws Exception {
        for (Role role : List.of(Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            String token = tokenOf(role);
            diagnoses("lumbago", token).andExpect(status().isForbidden());
            patients(tag, token).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/physio/patients")).andExpect(status().isUnauthorized());
    }

    private ResultActions diagnoses(String query, String token) throws Exception {
        return mockMvc.perform(get("/api/icd10-diagnoses").param("q", query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions patients(String query, String token) throws Exception {
        return mockMvc.perform(get("/api/physio/patients").param("q", query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String tokenOf(Role role) {
        User user = userRepository.save(User.create("lista-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de prueba", role, null, Instant.now()));
        return tokenService.issue(user, Instant.now()).token();
    }

}
