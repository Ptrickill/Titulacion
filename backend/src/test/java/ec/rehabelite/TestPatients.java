package ec.rehabelite;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic patients for integration tests, inserted with SQL until the patients module can register them
 * (HU-05). National IDs are random but carry a valid Ecuadorian check digit (modulo 10).
 */
public final class TestPatients {

    private TestPatients() {
    }

    /** Inserts a patient and returns its id. */
    public static Long insert(JdbcTemplate jdbc, String firstName, String lastName) {
        String nationalId = randomNationalId();
        return jdbc.queryForObject("""
                INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                      phone, email, address, occupation, education_level, health_insurance)
                VALUES (?, ?, ?, ?, DATE '1985-04-12', 'FEMALE', '0990000000', 'paciente@rehabelite.test',
                        'Dirección de prueba', 'Ocupación de prueba', 'Superior', 'Ninguno')
                RETURNING id""", Long.class, nationalId, "HC-" + nationalId, firstName, lastName);
    }

    public static String nationalIdOf(JdbcTemplate jdbc, Long patientId) {
        return jdbc.queryForObject("SELECT national_id FROM patients WHERE id = ?", String.class, patientId);
    }

    /** Province 01–24, third digit 0–5 (natural person), six random digits and the modulo 10 check digit. */
    public static String randomNationalId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String base = "%02d%d%06d".formatted(random.nextInt(1, 25), random.nextInt(0, 6), random.nextInt(1_000_000));
        return base + checkDigit(base);
    }

    static int checkDigit(String firstNineDigits) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int product = Character.digit(firstNineDigits.charAt(i), 10) * (i % 2 == 0 ? 2 : 1);
            sum += product > 9 ? product - 9 : product;
        }
        return (10 - sum % 10) % 10;
    }

}
