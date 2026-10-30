package ec.rehabelite.patients.domain;

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

        assertThat(accounts).hasSize(6).allMatch(email -> email.endsWith("@dev-seed.rehabelite.test"));
    }

    @Test
    void everyStatementIsIdempotent() {
        assertThat(script).contains("ON CONFLICT (email) DO NOTHING", "ON CONFLICT (national_id) DO NOTHING",
                "AND p.portal_user_id IS NULL");
    }

}
