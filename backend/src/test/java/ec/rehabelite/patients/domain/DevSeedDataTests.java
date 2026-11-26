package ec.rehabelite.patients.domain;

import ec.rehabelite.shared.domain.NationalId;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** TEC-06: the development seed script only holds valid, synthetic data and never a password or its hash. */
class DevSeedDataTests {

    private static final String SEED_SCRIPT = "db/dev-seed/R__dev_seed.sql";

    /** First two values of each patient row: national_id and medical_record_number. */
    private static final Pattern PATIENT_ROW = Pattern.compile("\\('(\\d{10})', '(\\d{10})',");

    private static String script;

    @BeforeAll
    static void readScript() throws IOException {
        script = new ClassPathResource(SEED_SCRIPT).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void hasFifteenPatientsWithValidDistinctNationalIds() {
        List<String> nationalIds = new ArrayList<>();
        Matcher row = PATIENT_ROW.matcher(script);
        while (row.find()) {
            String nationalId = row.group(1);
            assertThat(NationalId.isValid(nationalId)).as("national ID %s", nationalId).isTrue();
            assertThat(row.group(2)).as("medical record number of %s", nationalId).isEqualTo(nationalId);
            nationalIds.add(nationalId);
        }

        assertThat(nationalIds).hasSize(15).doesNotHaveDuplicates();
    }

    /**
     * MEJ-02: complete staff records with valid national IDs, none of them a patient's, and valid phones. MEJ-04
     * adds a third physiotherapist (Paredes) and MEJ-05 the reception (Salazar).
     */
    @Test
    void theFiveStaffRecordsHaveValidNationalIdsThatNoPatientHas() {
        Matcher row = Pattern.compile(
                "\\('([^']+@dev-seed\\.rehabelite\\.test)', '(\\d{10})', '[^']+', '[^']+', DATE '[\\d-]+',\\s+'[A-Z]+', "
                        + "'(\\d+)'").matcher(script);
        List<String> nationalIds = new ArrayList<>();
        while (row.find()) {
            assertThat(NationalId.isValid(row.group(2))).as("national ID of %s", row.group(1)).isTrue();
            assertThat(ec.rehabelite.shared.domain.PhoneNumber.isValid(row.group(3))).as("phone of %s", row.group(1))
                    .isTrue();
            nationalIds.add(row.group(2));
        }
        List<String> patientIds = new ArrayList<>();
        Matcher patients = PATIENT_ROW.matcher(script);
        while (patients.find()) {
            patientIds.add(patients.group(1));
        }

        assertThat(nationalIds).hasSize(5).doesNotHaveDuplicates().doesNotContainAnyElementsOf(patientIds);
        assertThat(script).contains("ON CONFLICT (user_id) DO UPDATE", "WHERE staff_records.national_id IS NULL");
    }

    @Test
    void containsNoPasswordHash() {
        assertThat(script).doesNotContainPattern("\\$2[aby]\\$");
        assertThat(script).doesNotContainIgnoringCase("DEV_SEED_PASSWORD=");
    }

    @Test
    void everyAccountUsesTheSeedDomain() {
        Matcher emails = Pattern.compile("'([^']+@[^']+)', 'dev-seed:password-set-at-startup'").matcher(script);
        List<String> accounts = new ArrayList<>();
        while (emails.find()) {
            accounts.add(emails.group(1));
        }

        assertThat(accounts).hasSize(8).allMatch(email -> email.endsWith("@dev-seed.rehabelite.test"));
    }

    @Test
    void everyStatementIsIdempotent() {
        assertThat(script).contains("ON CONFLICT (email) DO NOTHING", "ON CONFLICT (national_id) DO NOTHING",
                "AND p.portal_user_id IS NULL",
                // MEJ-04: schedules, clinical records, treatments, responsibles and the closure, each only once
                "WHERE NOT EXISTS (SELECT 1 FROM staff_schedule_slots existing",
                "ON CONFLICT (patient_id) DO NOTHING",
                "WHERE NOT EXISTS (SELECT 1 FROM treatments other",
                "AND NOT EXISTS (SELECT 1 FROM responsible_assignments other",
                "AND NOT EXISTS (SELECT 1 FROM treatment_status_changes other");
    }

}
