package ec.rehabelite.patients.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientTests {

    private static final Instant NOW = Instant.parse("2026-10-29T14:00:00Z");
    private static final LocalDate ADMISSION = LocalDate.of(2026, 10, 29);

    @Test
    void medicalRecordNumberIsTheNationalId() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), " ", null), 3L,
                NOW);

        assertThat(patient.getMedicalRecordNumber()).isEqualTo("1710034065");
        assertThat(patient.getNationalId()).isEqualTo("1710034065");
        assertThat(patient.getAdmissionDate()).isEqualTo(ADMISSION);
        assertThat(patient.getCreatedBy()).isEqualTo(3L);
        assertThat(patient.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void personalDataIsTrimmedAndBlankOptionalValuesAreNull() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), " ", null), 3L,
                NOW);

        assertThat(patient.getFirstName()).isEqualTo("Juan Carlos");
        assertThat(patient.getLastName()).isEqualTo("Pérez Gómez");
        assertThat(patient.getEmail()).isEqualTo("juan.perez@correo.ec");
        assertThat(patient.getEthnicSelfIdentification()).isNull();
        assertThat(patient.getReligion()).isNull();
    }

    @Test
    void ageIsCalculatedFromTheBirthDate() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), "Mestizo",
                "Católica"), 3L, NOW);

        assertThat(patient.ageOn(LocalDate.of(2026, 3, 14))).isEqualTo(29);
        assertThat(patient.ageOn(LocalDate.of(2026, 3, 15))).isEqualTo(30);
        assertThat(patient.ageOn(LocalDate.of(2026, 10, 29))).isEqualTo(30);
    }

    @Test
    void babiesAreZeroYearsOld() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(2026, 5, 2), null, null), 3L,
                NOW);

        assertThat(patient.ageOn(ADMISSION)).isZero();
    }

    @Test
    void birthDateCannotBeAfterTheAdmissionDate() {
        assertThatThrownBy(() -> Patient.register("1710034065", ADMISSION, data(ADMISSION.plusDays(1), null, null),
                3L, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Patient.BORN_AFTER_ADMISSION);
    }

    @Test
    void updateReplacesPersonalDataButNeverTheNationalIdOrRecordNumber() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), null, null), 3L,
                NOW);
        PersonalData changed = new PersonalData("Juan", "Pérez", LocalDate.of(1996, 3, 16), Sex.MALE, "0991112233",
                "nuevo@correo.ec", "Cumbayá", "Arquitecto", "Posgrado", "Seguro privado", "Mestizo", "Ninguna");
        Instant later = NOW.plusSeconds(3600);

        boolean updated = patient.update(ADMISSION.minusDays(2), changed, 7L, later);

        assertThat(updated).isTrue();
        assertThat(patient.personalData()).isEqualTo(changed);
        assertThat(patient.getAdmissionDate()).isEqualTo(ADMISSION.minusDays(2));
        assertThat(patient.getNationalId()).isEqualTo("1710034065");
        assertThat(patient.getMedicalRecordNumber()).isEqualTo("1710034065");
        assertThat(patient.getUpdatedBy()).isEqualTo(7L);
        assertThat(patient.getUpdatedAt()).isEqualTo(later);
        assertThat(patient.getCreatedBy()).isEqualTo(3L);
    }

    @Test
    void updateWithTheSameDataChangesNothing() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), "Mestizo", null),
                3L, NOW);

        // Same values, written differently (spaces, case): normalized to the stored ones
        boolean updated = patient.update(ADMISSION, data(LocalDate.of(1996, 3, 15), " Mestizo ", " "), 7L, NOW);

        assertThat(updated).isFalse();
        assertThat(patient.getUpdatedBy()).isNull();
        assertThat(patient.getUpdatedAt()).isNull();
    }

    @Test
    void updateKeepsTheBirthDateRule() {
        Patient patient = Patient.register("1710034065", ADMISSION, data(LocalDate.of(1996, 3, 15), null, null), 3L,
                NOW);

        assertThatThrownBy(() -> patient.update(LocalDate.of(1990, 1, 1), patient.personalData(), 7L, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Patient.BORN_AFTER_ADMISSION);
        assertThat(patient.getAdmissionDate()).isEqualTo(ADMISSION);
        assertThat(patient.getUpdatedAt()).isNull();
    }

    static PersonalData data(LocalDate birthDate, String ethnicSelfIdentification, String religion) {
        return new PersonalData(" Juan Carlos ", "Pérez Gómez ", birthDate, Sex.MALE, "+593 98 765 4321",
                " Juan.Perez@Correo.ec", "Av. Ilaló y Río Pita", "Ingeniero civil", "Superior", "IESS",
                ethnicSelfIdentification, religion);
    }

}
