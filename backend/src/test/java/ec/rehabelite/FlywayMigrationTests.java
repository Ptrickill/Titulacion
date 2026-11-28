package ec.rehabelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.shared.crypto.ClinicalTextEncryptor;
import ec.rehabelite.shared.crypto.EncryptClinicalTextMigration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Checks the Flyway migrations against PostgreSQL 16 (Testcontainers).
 */
class FlywayMigrationTests extends AbstractIntegrationTest {

    private final Flyway flyway;
    private final JdbcTemplate jdbc;
    private final EncryptClinicalTextMigration encryptClinicalText;
    private final ClinicalTextEncryptor encryptor;

    @Autowired
    FlywayMigrationTests(Flyway flyway, JdbcTemplate jdbc, EncryptClinicalTextMigration encryptClinicalText,
            ClinicalTextEncryptor encryptor) {
        this.flyway = flyway;
        this.jdbc = jdbc;
        this.encryptClinicalText = encryptClinicalText;
        this.encryptor = encryptor;
    }

    /** Flyway on another database with the SQL migrations and V11, the Java one (MEJ-06), as the application has. */
    private FluentConfiguration configure(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .javaMigrations(encryptClinicalText);
    }

    @Test
    void runsOnPostgres16() {
        String version = jdbc.queryForObject("SHOW server_version", String.class);
        assertThat(version).startsWith("16.");
    }

    @Test
    void appliesV1ToV11() {
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(flyway.info().applied()).allMatch(migration -> migration.getState().isApplied());
        assertThat(flyway.info().pending()).isEmpty();
    }

    /** HU-18: Envers tables of V3 (the context starting at all proves they match the mapping, ddl-auto=validate). */
    @Test
    void v3CreatesTheEnversTables() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('revinfo', 'treatments_aud', 'therapy_sessions_aud')
                ORDER BY table_name""", String.class))
                .containsExactly("revinfo", "therapy_sessions_aud", "treatments_aud");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'revinfo' AND column_name = 'user_id'""", String.class)).isEqualTo("YES");
    }

    /**
     * Each _aud table mirrors its table column by column, with the same type and length (HU-10 maps TherapySession
     * with @Audited on therapy_sessions_aud as created in V3). A change to either table must change both.
     */
    @Test
    void theVersionTablesHaveTheSameColumnsAsTheirTables() {
        String columns = """
                SELECT column_name || ':' || data_type || ':' || coalesce(character_maximum_length::text, '-')
                FROM information_schema.columns
                WHERE table_name = ? AND column_name NOT IN ('rev', 'revtype') ORDER BY column_name""";
        assertThat(jdbc.queryForList(columns, String.class, "therapy_sessions_aud"))
                .isEqualTo(jdbc.queryForList(columns, String.class, "therapy_sessions"));
        assertThat(jdbc.queryForList(columns, String.class, "treatments_aud"))
                .isEqualTo(jdbc.queryForList(columns, String.class, "treatments"));
    }

    @Test
    void icd10CatalogHasRows() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM icd10_diagnoses", Integer.class);
        assertThat(total).isGreaterThan(0);
    }

    @Test
    void normalizeTextIgnoresAccentsAndCase() {
        String normalized = jdbc.queryForObject("SELECT normalize_text('ÁLVAREZ Peña')", String.class);
        assertThat(normalized).isEqualTo("alvarez pena");
    }

    /**
     * MEJ-01: V5 on a database that already has accounts (a fresh database in the same container, migrated to V4,
     * then to V5). The existing accounts keep their password and count as verified since their creation.
     */
    @Test
    void v5KeepsTheExistingAccountsActivated() {
        String database = "mej01_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("4").load().migrate();
            other.update("""
                    INSERT INTO users (email, password_hash, full_name, role, created_at)
                    VALUES ('existente@rehabelite.test', '$2a$10$hash', 'Cuenta Existente', 'PHYSIOTHERAPIST',
                            '2026-09-01T10:00:00Z')""");

            configure(dataSource).load().migrate();

            Map<String, Object> account = other.queryForMap("""
                    SELECT password_hash, email_verified_at = created_at AS verified_since_creation, session_version
                    FROM users WHERE email = 'existente@rehabelite.test'""");
            assertThat(account).containsEntry("password_hash", "$2a$10$hash")
                    .containsEntry("verified_since_creation", true)
                    .containsEntry("session_version", 0);
            assertThat(other.queryForObject("SELECT count(*) FROM account_tokens", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /**
     * MEJ-03: V6 on a database that already has patients (a fresh database in the same container, migrated to V5,
     * then to V6). "Ninguno", in any case and with spaces, becomes no insurance and any other value yes; the former
     * free-text address is kept as the reference of an incomplete address.
     */
    @Test
    void v6ConvertsTheInsuranceAndKeepsTheFormerAddressAsTheReference() {
        String database = "mej03_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("5").load().migrate();
            String insert = """
                    INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                          phone, email, address, occupation, education_level, health_insurance)
                    VALUES (?, ?, 'Nombre', 'Apellido', DATE '1980-01-01', 'FEMALE', '+593 99 111 2233',
                            'p@rehabelite.test', ?, 'Ocupación', 'Superior', ?)""";
            other.update(insert, "1710034065", "1710034065", "Av. Ilaló y Río Pita, Valle de los Chillos", "IESS");
            other.update(insert, "0102030405", "0102030405", "Calle Larga 8-40, Cuenca", "Ninguno");
            other.update(insert, "0912345675", "0912345675", "Mz 3 V 12, Guayaquil", "  ninguno ");
            other.update(insert, "1802345671", "1802345671", "Av. Cevallos, Ambato", "Seguro privado");

            configure(dataSource).load().migrate();

            assertThat(other.queryForList("""
                    SELECT national_id || ':' || has_health_insurance || ':' || address_reference || ':'
                           || (province_code IS NULL AND parish_code IS NULL AND main_street IS NULL)
                    FROM patients ORDER BY national_id""", String.class)).containsExactly(
                    "0102030405:false:Calle Larga 8-40, Cuenca:true",
                    "0912345675:false:Mz 3 V 12, Guayaquil:true",
                    "1710034065:true:Av. Ilaló y Río Pita, Valle de los Chillos:true",
                    "1802345671:true:Av. Cevallos, Ambato:true");
            assertThat(other.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_name = 'patients' AND column_name IN ('address', 'health_insurance')""", String.class))
                    .isEmpty();
            assertThat(other.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'patients' "
                            + "AND column_name = 'has_health_insurance'", String.class)).isEqualTo("NO");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /** MEJ-03: an address has all its parts or none, and the codes are nested. */
    @Test
    void v6RejectsAPartialOrInconsistentAddress() {
        String base = """
                INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                      phone, email, province_code, canton_code, parish_code, main_street,
                                      cross_street, occupation, education_level, has_health_insurance)
                VALUES (?, ?, 'N', 'A', DATE '1980-01-01', 'MALE', '0991234567', 'p@rehabelite.test', ?, ?, ?, ?, ?,
                        'O', 'Superior', true)""";
        String partialId = TestPatients.randomNationalId();
        assertThatThrownBy(() -> jdbc.update(base, partialId, partialId, "17", "1701", "170156", "Calle", null))
                .rootCause().hasMessageContaining("ck_patients_address_parts");
        String crossedId = TestPatients.randomNationalId();
        assertThatThrownBy(() -> jdbc.update(base, crossedId, crossedId, "09", "1701", "170156", "Calle", "Otra"))
                .rootCause().hasMessageContaining("ck_patients_address_codes");
    }

    /**
     * MEJ-02: V7 on a database with V6 data (a fresh database in the same container, migrated to V6, then to V7).
     * Every staff account gets its record with its email and its names split from full_name, and the rest empty; a
     * patient account is left alone, but its name becomes its patient's names.
     */
    @Test
    void v7GivesEveryStaffAccountItsRecordWithTheDataTheAccountHas() {
        String database = "mej02_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("6").load().migrate();
            String account = """
                    INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
                    VALUES (?, '$2a$10$hash', ?, ?, now()) RETURNING id""";
            other.queryForObject(account, Long.class, "uno@rehabelite.test", "Administrador", "ADMINISTRATOR");
            other.queryForObject(account, Long.class, "dos@rehabelite.test", "Lucía  Mora", "PHYSIOTHERAPIST");
            other.queryForObject(account, Long.class, "tres@rehabelite.test", "Paula Herrera Vásconez",
                    "OPERATIONS_DIRECTOR");
            other.queryForObject(account, Long.class, "cuatro@rehabelite.test", "María José Pérez López",
                    "PHYSIOTHERAPIST");
            Long patientAccount = other.queryForObject(account, Long.class, "paciente@rehabelite.test",
                    "Nombre Escrito A Mano", "PATIENT");
            other.update("""
                    INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                          phone, email, occupation, education_level, has_health_insurance,
                                          portal_user_id)
                    VALUES ('1710034065', '1710034065', 'Rosa Elena', 'Ortiz Vega', DATE '1968-04-12', 'FEMALE',
                            '0990000101', 'rosa@rehabelite.test', 'Contadora', 'Superior', true, ?)""",
                    patientAccount);

            configure(dataSource).load().migrate();

            assertThat(other.queryForList("""
                    SELECT u.email || '|' || s.first_name || '|' || s.last_name || '|' || s.email || '|' || u.full_name
                           || '|' || (s.national_id IS NULL AND s.birth_date IS NULL AND s.sex IS NULL
                                      AND s.phone IS NULL AND s.parish_code IS NULL AND s.main_street IS NULL)
                    FROM staff_records s JOIN users u ON u.id = s.user_id ORDER BY u.email""", String.class))
                    .containsExactly(
                            "cuatro@rehabelite.test|María José|Pérez López|cuatro@rehabelite.test"
                                    + "|María José Pérez López|true",
                            "dos@rehabelite.test|Lucía|Mora|dos@rehabelite.test|Lucía Mora|true",
                            "tres@rehabelite.test|Paula|Herrera Vásconez|tres@rehabelite.test"
                                    + "|Paula Herrera Vásconez|true",
                            "uno@rehabelite.test|Administrador||uno@rehabelite.test|Administrador|true");
            assertThat(other.queryForObject("SELECT count(*) FROM staff_records WHERE user_id = ?", Integer.class,
                    patientAccount)).isZero();
            assertThat(other.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, patientAccount))
                    .isEqualTo("Rosa Elena Ortiz Vega");
            assertThat(other.queryForObject("SELECT count(*) FROM users WHERE pending_email IS NOT NULL",
                    Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /** MEJ-02: one record per account, unique national ID and email, and the address rules of V6. */
    @Test
    void v7KeepsOneStaffRecordPerAccountWithUniqueNationalIdAndEmail() {
        Long first = insertAccount("PHYSIOTHERAPIST");
        Long second = insertAccount("PHYSIOTHERAPIST");
        String nationalId = TestPatients.randomNationalId();
        String email = "registro-" + UUID.randomUUID() + "@rehabelite.test";
        String insert = "INSERT INTO staff_records (user_id, national_id, first_name, last_name, email) "
                + "VALUES (?, ?, 'N', 'A', ?)";
        jdbc.update(insert, first, nationalId, email);

        assertThatThrownBy(() -> jdbc.update(insert, first, null, "otro-" + email))
                .rootCause().hasMessageContaining("uq_staff_records_user_id");
        assertThatThrownBy(() -> jdbc.update(insert, second, nationalId, "otro-" + email))
                .rootCause().hasMessageContaining("uq_staff_records_national_id");
        assertThatThrownBy(() -> jdbc.update(insert, second, null, email))
                .rootCause().hasMessageContaining("uq_staff_records_email");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO staff_records (user_id, first_name, last_name, email, province_code, canton_code,
                                           parish_code, main_street)
                VALUES (?, 'N', 'A', ?, '17', '1701', '170156', 'Calle')""", second, "x-" + email))
                .rootCause().hasMessageContaining("ck_staff_records_address_parts");
    }

    /** MEJ-02: no two accounts share a pending new email, and links can now confirm an email. */
    @Test
    void v7AddsTheUniquePendingEmailAndTheEmailChangeLink() {
        Long first = insertAccount("ADMINISTRATOR");
        Long second = insertAccount("ADMINISTRATOR");
        String pending = "nuevo-" + UUID.randomUUID() + "@rehabelite.test";
        jdbc.update("UPDATE users SET pending_email = ? WHERE id = ?", pending, first);

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET pending_email = ? WHERE id = ?", pending, second))
                .rootCause().hasMessageContaining("uq_users_pending_email");
        jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'EMAIL_CHANGE', ?, now() + interval '48 hours')""", first,
                UUID.randomUUID().toString().replace("-", "").repeat(2));
    }

    /**
     * MEJ-04: V8 on a database with V7 data (a fresh database in the same container, migrated to V7, then to V8). The
     * existing records keep their data and start without slots; btree_gist and the time range make the EXCLUDE
     * constraint work there too.
     */
    @Test
    void v8AddsTheScheduleSlotsToADatabaseWithStaffRecords() {
        String database = "mej04_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("7").load().migrate();
            Long userId = other.queryForObject("""
                    INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
                    VALUES ('fisio@rehabelite.test', '$2a$10$hash', 'Lucía Mora', 'PHYSIOTHERAPIST', now())
                    RETURNING id""", Long.class);
            Long staffId = other.queryForObject("""
                    INSERT INTO staff_records (user_id, first_name, last_name, email)
                    VALUES (?, 'Lucía', 'Mora', 'fisio@rehabelite.test') RETURNING id""", Long.class, userId);

            configure(dataSource).load().migrate();

            assertThat(other.queryForObject("SELECT first_name || ' ' || last_name FROM staff_records WHERE id = ?",
                    String.class, staffId)).isEqualTo("Lucía Mora");
            assertThat(other.queryForObject("SELECT count(*) FROM staff_schedule_slots", Integer.class)).isZero();
            assertThat(other.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
                    Integer.class)).isEqualTo(1);
            String slot = "INSERT INTO staff_schedule_slots (staff_record_id, day_of_week, start_time, end_time) "
                    + "VALUES (?, 1, CAST(? AS time), CAST(? AS time))";
            other.update(slot, staffId, "08:00", "12:00");
            other.update(slot, staffId, "12:00", "14:00");
            assertThatThrownBy(() -> other.update(slot, staffId, "13:00", "15:00"))
                    .rootCause().hasMessageContaining("ex_staff_schedule_slots_overlap");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /**
     * MEJ-05: V9 on a database with V8 data. No account changes (the administrators become the reception with the
     * same access), and from then on the trigger keeps at least one active operations director: the last one is never
     * deactivated, demoted or left unverified, while a database without directors can still be updated.
     */
    @Test
    void v9KeepsAnActiveOperationsDirectorOnADatabaseWithV8Data() {
        String database = "mej05_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("8").load().migrate();
            String account = """
                    INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
                    VALUES (?, '$2a$10$hash', 'Cuenta', ?, now()) RETURNING id""";
            Long admin = other.queryForObject(account, Long.class, "recepcion@rehabelite.test", "ADMINISTRATOR");
            Long physio = other.queryForObject(account, Long.class, "fisio@rehabelite.test", "PHYSIOTHERAPIST");

            configure(dataSource).load().migrate();

            assertThat(other.queryForList("SELECT role || ':' || active FROM users ORDER BY id", String.class))
                    .containsExactly("ADMINISTRATOR:true", "PHYSIOTHERAPIST:true");
            // Without any director, the other accounts still change
            other.update("UPDATE users SET active = false WHERE id = ?", physio);
            other.update("UPDATE users SET active = true WHERE id = ?", physio);

            Long first = other.queryForObject(account, Long.class, "direccion1@rehabelite.test",
                    "OPERATIONS_DIRECTOR");
            Long second = other.queryForObject(account, Long.class, "direccion2@rehabelite.test",
                    "OPERATIONS_DIRECTOR");
            other.update("UPDATE users SET active = false WHERE id = ?", second);
            for (String change : new String[] {"active = false", "role = 'ADMINISTRATOR'",
                    "email_verified_at = NULL"}) {
                assertThatThrownBy(() -> other.update("UPDATE users SET " + change + " WHERE id = ?", first))
                        .rootCause().hasMessageContaining("At least one active operations director must remain");
            }
            // A pending director does not count; once the second one is active again, the first one may leave
            other.update("UPDATE users SET active = true WHERE id = ?", second);
            other.update("UPDATE users SET active = false WHERE id = ?", first);
            assertThat(other.queryForObject("""
                    SELECT count(*) FROM users
                    WHERE role = 'OPERATIONS_DIRECTOR' AND active AND email_verified_at IS NOT NULL""",
                    Integer.class)).isEqualTo(1);
            assertThat(other.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, admin)).isTrue();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /**
     * MEJ-06: V10 and V11 on a database at V9 with plain clinical text in every encrypted table, the Envers ones and
     * ai_summaries included. V11 encrypts every value (they decrypt to the original), running the encryption again
     * changes nothing, and V10 leaves the existing patients without consents ("Consentimiento pendiente").
     */
    @Test
    void v11EncryptsTheExistingClinicalTextOfADatabaseAtV9() throws Exception {
        String database = "mej06_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            JdbcTemplate other = new JdbcTemplate(dataSource);
            configure(dataSource).target("9").load().migrate();
            Long physio = other.queryForObject("""
                    INSERT INTO users (email, password_hash, full_name, role, email_verified_at)
                    VALUES ('fisio@rehabelite.test', '$2a$10$hash', 'Fisio', 'PHYSIOTHERAPIST', now())
                    RETURNING id""", Long.class);
            Long patient = other.queryForObject("""
                    INSERT INTO patients (national_id, medical_record_number, first_name, last_name, birth_date, sex,
                                          phone, email, occupation, education_level, has_health_insurance,
                                          address_reference)
                    VALUES ('1710034065', '1710034065', 'Rosa', 'Ortiz', DATE '1968-04-12', 'FEMALE', '0990000101',
                            'rosa@rehabelite.test', 'Contadora', 'Superior', true, 'Texto anterior')
                    RETURNING id""", Long.class);
            Long record = other.queryForObject("""
                    INSERT INTO clinical_records (patient_id, habits, physical_activity, allergies, current_medication,
                                                  personal_history, surgical_history, family_history)
                    VALUES (?, 'No fuma', 'Camina', 'Penicilina', 'Ninguna', 'Hipertensión', 'Ninguno', 'Ninguno')
                    RETURNING id""", Long.class, patient);
            Long treatment = other.queryForObject("""
                    INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness,
                                            referring_physician, complementary_tests, severity, pain_type,
                                            pain_location, initial_pain_score, pain_frequency, session_frequency,
                                            goal, planned_sessions)
                    VALUES (?, 'M54.5', 'Dolor lumbar', 'Tres semanas', 'Dr. Andrade', 'Radiografía', 'MODERATE',
                            'Punzante', 'Lumbar', 6, 'Intermitente', '3 por semana', 'Reducir el dolor', 10)
                    RETURNING id""", Long.class, record);
            Long rev = other.queryForObject("INSERT INTO revinfo (revision_timestamp) VALUES (now()) RETURNING rev",
                    Long.class);
            other.update("""
                    INSERT INTO treatments_aud (id, rev, revtype, chief_complaint, current_illness, complementary_tests,
                                                goal)
                    VALUES (?, ?, 0, 'Dolor lumbar', 'Tres semanas', 'Radiografía', 'Reducir el dolor')""",
                    treatment, rev);
            other.update("""
                    INSERT INTO treatment_status_changes (treatment_id, user_id, previous_status, new_status, reason)
                    VALUES (?, ?, 'ACTIVE', 'ABANDONED', 'No asiste')""", treatment, physio);
            Long session = other.queryForObject("""
                    INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date,
                                                  session_time, session_type, activities_performed,
                                                  clinical_observations, patient_instructions, pain_score,
                                                  progress_status, status)
                    VALUES (?, ?, 1, DATE '2026-10-01', TIME '09:00', 'Fisioterapia', 'TENS', 'Buena tolerancia',
                            NULL, 5, 'IMPROVING', 'COMPLETED')
                    RETURNING id""", Long.class, treatment, physio);
            other.update("""
                    INSERT INTO therapy_sessions_aud (id, rev, revtype, activities_performed, clinical_observations,
                                                      patient_instructions)
                    VALUES (?, ?, 0, 'TENS', 'Buena tolerancia', 'Calor local')""", session, rev);
            other.update("""
                    INSERT INTO ai_summaries (treatment_id, generated_by_id, generated_content)
                    VALUES (?, ?, 'Resumen generado')""", treatment, physio);

            configure(dataSource).load().migrate();

            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("SELECT allergies FROM clinical_records", "Penicilina");
            expected.put("SELECT chief_complaint FROM treatments", "Dolor lumbar");
            expected.put("SELECT goal FROM treatments_aud", "Reducir el dolor");
            expected.put("SELECT reason FROM treatment_status_changes", "No asiste");
            expected.put("SELECT clinical_observations FROM therapy_sessions", "Buena tolerancia");
            expected.put("SELECT patient_instructions FROM therapy_sessions_aud", "Calor local");
            expected.put("SELECT generated_content FROM ai_summaries", "Resumen generado");
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String stored = other.queryForObject(entry.getKey(), String.class);
                assertThat(stored).as(entry.getKey()).startsWith("enc:v1:");
                assertThat(TestClinicalText.decrypt(stored)).as(entry.getKey()).isEqualTo(entry.getValue());
            }
            assertThat(other.queryForObject("SELECT patient_instructions FROM therapy_sessions", String.class))
                    .isNull();
            assertThat(other.queryForObject("SELECT final_content FROM ai_summaries", String.class)).isNull();
            assertThat(other.queryForObject("""
                    SELECT count(*) FROM clinical_records
                    WHERE habits LIKE 'enc:v1:%' AND physical_activity LIKE 'enc:v1:%'
                      AND family_history LIKE 'enc:v1:%'""", Integer.class)).isEqualTo(1);

            String before = other.queryForObject("SELECT habits FROM clinical_records", String.class);
            try (var connection = dataSource.getConnection()) {
                assertThat(encryptor.encryptPlainText(connection)).isZero();
            }
            assertThat(other.queryForObject("SELECT habits FROM clinical_records", String.class)).isEqualTo(before);

            // V10: the existing patient has no consent yet, its email may become null, and the new tables exist
            assertThat(other.queryForObject("SELECT count(*) FROM patient_consents", Integer.class)).isZero();
            assertThat(other.queryForObject("SELECT count(*) FROM data_subject_requests", Integer.class)).isZero();
            other.update("UPDATE patients SET email = NULL, phone = NULL, blocked_at = now() WHERE id = ?", patient);
            assertThatThrownBy(() -> other.update("""
                    INSERT INTO patient_consents (patient_id, consent_type, text_version, channel)
                    VALUES (?, 'DATA_PROCESSING', '1', 'ERASURE')""", patient))
                    .rootCause().hasMessageContaining("ck_patient_consents_channel");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    /** MEJ-06: one open consent per patient and type; a closed request has its response and its closing date. */
    @Test
    void v10KeepsOneOpenConsentPerTypeAndClosesRequestsWithAResponse() {
        Long patient = TestPatients.insertWithoutConsent(jdbc, "Paciente", "De Restricciones");
        String consent = """
                INSERT INTO patient_consents (patient_id, consent_type, text_version, channel)
                VALUES (?, 'EMAIL_COMMUNICATIONS', '1', 'PORTAL')""";
        jdbc.update(consent, patient);
        assertThatThrownBy(() -> jdbc.update(consent, patient))
                .rootCause().hasMessageContaining("uq_patient_consents_current");
        jdbc.update("UPDATE patient_consents SET revoked_at = now(), revocation_channel = 'PORTAL' "
                + "WHERE patient_id = ?", patient);
        jdbc.update(consent, patient);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO data_subject_requests (patient_id, request_type, received_on, status)
                VALUES (?, 'ACCESS', CURRENT_DATE, 'RESOLVED')""", patient))
                .rootCause().hasMessageContaining("ck_data_subject_requests_closing");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO data_subject_requests (patient_id, request_type, received_on)
                VALUES (?, 'DELETE', CURRENT_DATE)""", patient))
                .rootCause().hasMessageContaining("ck_data_subject_requests_type");
    }

    private Long insertAccount(String role) {
        return jdbc.queryForObject("INSERT INTO users (email, full_name, role) VALUES (?, 'Cuenta', ?) RETURNING id",
                Long.class, "cuenta-" + UUID.randomUUID() + "@rehabelite.test", role);
    }

    /** MEJ-01: a pending account has no password; a token stores only its 64-character hash, once. */
    @Test
    void v5AllowsAccountsWithoutPasswordAndStoresTokenHashesOnce() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, full_name, role) VALUES (?, 'Pendiente', 'PATIENT') RETURNING id""",
                Long.class, "pendiente-" + UUID.randomUUID() + "@rehabelite.test");
        String hash = "a".repeat(63) + "b";
        jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'ACTIVATION', ?, now() + interval '48 hours')""", userId, hash);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'PASSWORD_RESET', ?, now())""", userId, hash))
                .rootCause().hasMessageContaining("uq_account_tokens_token_hash");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at)
                VALUES (?, 'LOGIN', ?, now())""", userId, "c".repeat(64)))
                .rootCause().hasMessageContaining("ck_account_tokens_purpose");
    }

    /** HU-15: V4 adds EXPORT; the constraint allows exactly the values of AuditAction, and nothing else. */
    @Test
    void v4AllowsTheExportActionAndTheConstraintMatchesTheEnum() {
        jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('EXPORT', 'Report')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('PRINT', 'Report')"))
                .rootCause().hasMessageContaining("ck_audit_logs_action");
        String constraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_audit_logs_action'""",
                String.class);
        for (AuditAction action : AuditAction.values()) {
            assertThat(constraint).contains("'" + action.name() + "'");
        }
        assertThat(constraint.split("'::character varying").length - 1).isEqualTo(AuditAction.values().length);
    }

    @Test
    void auditLogsAreAppendOnly() {
        jdbc.update("INSERT INTO audit_logs (action, entity_type) VALUES ('LOGIN', 'users')");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET entity_type = 'other'"))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs"))
                .rootCause().hasMessageContaining("append-only");
    }

}
