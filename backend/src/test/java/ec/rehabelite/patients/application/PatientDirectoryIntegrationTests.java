package ec.rehabelite.patients.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-12: the patient portal finds its patient only by the portal account linked in patients.portal_user_id. */
class PatientDirectoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PatientDirectory patientDirectory;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void thePatientOfAPortalAccountIsTheOneLinkedToIt() {
        Long account = insertPortalAccount();
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        TestPatients.insert(jdbc, "Luis Alberto", "Quishpe Tipán");
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", account, patientId);

        assertThat(patientDirectory.findByPortalUser(account)).hasValueSatisfying(patient -> {
            assertThat(patient.id()).isEqualTo(patientId);
            assertThat(patient.nationalId()).isEqualTo(TestPatients.nationalIdOf(jdbc, patientId));
            assertThat(patient.firstName()).isEqualTo("Rosa Elena");
            assertThat(patient.lastName()).isEqualTo("Ortiz Vega");
        });
        assertThat(patientDirectory.findByPortalUser(account)).map(PatientCard::id)
                .isEqualTo(patientDirectory.find(patientId).map(PatientCard::id));
    }

    @Test
    void anAccountWithoutALinkedPatientHasNone() {
        assertThat(patientDirectory.findByPortalUser(insertPortalAccount())).isEmpty();
        assertThat(patientDirectory.findByPortalUser(999_999_999L)).isEmpty();
    }

    private Long insertPortalAccount() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Cuenta de portal', 'PATIENT')
                RETURNING id""", Long.class, "directorio-" + UUID.randomUUID() + "@rehabelite.test");
    }

}
