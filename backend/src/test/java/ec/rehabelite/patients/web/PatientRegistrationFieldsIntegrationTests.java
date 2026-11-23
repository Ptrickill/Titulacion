package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * MEJ-03 end to end: structured address checked against the INEC catalog, phone rule, health insurance yes/no,
 * names with a capital initial, and the patients registered before MEJ-03, whose data is completed on edit.
 */
@AutoConfigureMockMvc
class PatientRegistrationFieldsIntegrationTests extends AbstractIntegrationTest {

    private static final String PHONE_MESSAGE =
            "Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.create("admin-" + UUID.randomUUID() + "@rehabelite.test", "hash",
                "Admin", Role.ADMINISTRATOR, null, Instant.now()));
        adminToken = tokenService.issue(admin, Instant.now()).token();
    }

    @Test
    void registersTheStructuredAddressAndShowsItComposedWithTheCatalogNames() throws Exception {
        String nationalId = TestPatients.randomNationalId();

        register(body(nationalId, Map.of("firstName", "  juan   CARLOS ", "lastName", "PÉREZ de la torre",
                "hasHealthInsurance", "false", "phone", "022345678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Juan Carlos"))
                .andExpect(jsonPath("$.lastName").value("Pérez de la Torre"))
                .andExpect(jsonPath("$.phone").value("022345678"))
                .andExpect(jsonPath("$.hasHealthInsurance").value(false))
                .andExpect(jsonPath("$.address.provinceCode").value("17"))
                .andExpect(jsonPath("$.address.provinceName").value("Pichincha"))
                .andExpect(jsonPath("$.address.cantonName").value("Distrito Metropolitano de Quito"))
                .andExpect(jsonPath("$.address.parishName").value("Conocoto"))
                .andExpect(jsonPath("$.address.streetNumber").value("N12-34"))
                .andExpect(jsonPath("$.address.formatted").value("Av. Ilaló N12-34 y Río Pita, Conocoto, "
                        + "Distrito Metropolitano de Quito, Pichincha. Referencia: Frente al parque central"))
                .andExpect(jsonPath("$.address.complete").value(true))
                .andExpect(jsonPath("$.needsCompletion").value(false))
                .andExpect(jsonPath("$.healthInsurance").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT first_name, last_name, province_code, canton_code, parish_code, main_street, street_number,
                       cross_street, address_reference, has_health_insurance
                FROM patients WHERE national_id = ?""", nationalId);
        assertThat(row).containsEntry("first_name", "Juan Carlos")
                .containsEntry("last_name", "Pérez de la Torre")
                .containsEntry("province_code", "17")
                .containsEntry("canton_code", "1701")
                .containsEntry("parish_code", "170156")
                .containsEntry("main_street", "Av. Ilaló")
                .containsEntry("cross_street", "Río Pita")
                .containsEntry("has_health_insurance", false);
    }

    @Test
    void theNumberIsOptionalButEveryOtherPartOfTheAddressIsRequired() throws Exception {
        register(body(TestPatients.randomNationalId(), Map.of("address", """
                {"provinceCode":"09","cantonCode":"0901","parishCode":"090112","mainStreet":"Av. 9 de Octubre",\
                "crossStreet":"Boyacá","reference":"Junto al banco"}""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.formatted").value(
                        "Av. 9 de Octubre y Boyacá, Tarqui, Guayaquil, Guayas. Referencia: Junto al banco"));

        register(body(TestPatients.randomNationalId(), Map.of("address", """
                {"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":" ",\
                "crossStreet":"","reference":""}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address.mainStreet")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address.crossStreet")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address.reference")));
        register(body(TestPatients.randomNationalId(), Map.of("address", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("address"));
    }

    @Test
    void theParishMustBelongToTheCantonAndTheCantonToTheProvince() throws Exception {
        // Conocoto (Quito, Pichincha) with Guayaquil, and Quito with Guayas
        for (String address : new String[] {
            """
            {"provinceCode":"09","cantonCode":"0901","parishCode":"170156","mainStreet":"A","crossStreet":"B",\
            "reference":"C"}""",
            """
            {"provinceCode":"09","cantonCode":"1701","parishCode":"170156","mainStreet":"A","crossStreet":"B",\
            "reference":"C"}""",
            """
            {"provinceCode":"17","cantonCode":"1701","parishCode":"170199","mainStreet":"A","crossStreet":"B",\
            "reference":"C"}"""}) {
            register(body(TestPatients.randomNationalId(), Map.of("address", address)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("address.parishCode"))
                    .andExpect(jsonPath("$.errors[0].message")
                            .value("la parroquia debe pertenecer al cantón y el cantón a la provincia"));
        }
    }

    @Test
    void thePhoneMustBeAMobileOrALandlineWithItsProvinceCode() throws Exception {
        for (String phone : new String[] {"0991234567", "022345678", "072345678"}) {
            register(body(TestPatients.randomNationalId(), Map.of("phone", phone))).andExpect(status().isCreated());
        }
        for (String phone : new String[] {"+593 98 765 4321", "099123456", "09912345678", "012345678", "082345678",
            "0991 234 567", "1991234567"}) {
            register(body(TestPatients.randomNationalId(), Map.of("phone", phone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("phone"))
                    .andExpect(jsonPath("$.errors[0].message").value(PHONE_MESSAGE));
        }
    }

    @Test
    void aPatientRegisteredBeforeTheStructuredAddressHasDataToComplete() throws Exception {
        String nationalId = TestPatients.randomNationalId();
        Long patientId = jdbc.queryForObject("""
                INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                      phone, email, address_reference, occupation, education_level,
                                      has_health_insurance)
                VALUES (?, ?, 'Rosa Elena', 'Ortiz Vega', DATE '1968-04-12', 'FEMALE', '+593 99 111 2233',
                        'rosa@rehabelite.test', 'Calle Antigua N10-01, Quito', 'Contadora', 'Superior', true)
                RETURNING id""", Long.class, nationalId, nationalId);

        mockMvc.perform(get("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.complete").value(false))
                .andExpect(jsonPath("$.address.reference").value("Calle Antigua N10-01, Quito"))
                .andExpect(jsonPath("$.address.formatted").value("Calle Antigua N10-01, Quito"))
                .andExpect(jsonPath("$.address.parishName").doesNotExist())
                .andExpect(jsonPath("$.needsCompletion").value(true));
        mockMvc.perform(get("/api/patients").param("q", nationalId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].needsCompletion").value(true));

        // Editing it requires the complete address and a valid phone
        String update = updateBody(Map.of("phone", "+593 99 111 2233"));
        mockMvc.perform(put("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
        mockMvc.perform(put("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.complete").value(true))
                .andExpect(jsonPath("$.needsCompletion").value(false));
    }

    @Test
    void anOldNameInCapitalsCountsAsAChangeWhenItIsSavedAgain() throws Exception {
        Long patientId = TestPatients.insert(jdbc, "ROSA ELENA", "ORTIZ VEGA");
        String admissionDate = jdbc.queryForObject("SELECT admission_date::text FROM patients WHERE id = ?",
                String.class, patientId);
        // The same data the helper stored; only the capitalization of the names differs
        String sameData = updateBody(Map.of("admissionDate", admissionDate, "firstName", "ROSA ELENA",
                "lastName", "ORTIZ VEGA", "birthDate", "1985-04-12", "phone", "0990000000",
                "email", "paciente@rehabelite.test", "occupation", "Ocupación de prueba",
                "hasHealthInsurance", "false", "address", """
                        {"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Calle de prueba",\
                        "crossStreet":"Calle secundaria de prueba","reference":"Referencia de prueba"}"""));

        mockMvc.perform(put("/api/patients/{id}", patientId).header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(sameData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Rosa Elena"))
                .andExpect(jsonPath("$.lastName").value("Ortiz Vega"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String nationalId, Map<String, String> overrides) {
        Map<String, String> fields = new java.util.LinkedHashMap<>(Map.of(
                "nationalId", nationalId,
                "firstName", "Juan Carlos",
                "lastName", "Pérez Gómez",
                "birthDate", "1996-03-15",
                "sex", "MALE",
                "phone", "0987654321",
                "email", "juan.perez@correo.ec",
                "address", PatientRegistrationIntegrationTests.ADDRESS,
                "occupation", "Ingeniero civil",
                "educationLevel", "Superior"));
        fields.put("hasHealthInsurance", "true");
        fields.putAll(overrides);
        return json(fields);
    }

    private static String updateBody(Map<String, String> overrides) {
        Map<String, String> fields = new java.util.LinkedHashMap<>(Map.of(
                "admissionDate", "2026-01-15",
                "firstName", "Rosa Elena",
                "lastName", "Ortiz Vega",
                "birthDate", "1968-04-12",
                "sex", "FEMALE",
                "phone", "0991112233",
                "email", "rosa@rehabelite.test",
                "address", PatientRegistrationIntegrationTests.ADDRESS,
                "occupation", "Contadora",
                "educationLevel", "Superior"));
        fields.put("hasHealthInsurance", "true");
        fields.putAll(overrides);
        return json(fields);
    }

    private static String json(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        fields.forEach((key, value) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            boolean raw = value.startsWith("{") || value.equals("true") || value.equals("false") || value.equals("null");
            json.append('"').append(key).append("\":").append(raw ? value : '"' + value + '"');
        });
        return json.append('}').toString();
    }

}
