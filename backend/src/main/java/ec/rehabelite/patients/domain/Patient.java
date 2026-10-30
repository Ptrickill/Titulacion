package ec.rehabelite.patients.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registered patient: admission and personal data only, never clinical data. The age is not stored; it is
 * calculated from the birth date ({@link #ageOn(LocalDate)}).
 */
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    public static final String ALREADY_LINKED = "El paciente seleccionado ya tiene una cuenta de portal.";
    public static final String BORN_AFTER_ADMISSION =
            "La fecha de nacimiento no puede ser posterior a la fecha de ingreso.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fixed-length char(10) column: the national ID always has exactly 10 digits. */
    @Column(name = "national_id", nullable = false, length = 10)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String nationalId;

    @Column(name = "medical_record_number", nullable = false, length = 20)
    private String medicalRecordNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 100)
    private String occupation;

    @Column(name = "education_level", nullable = false, length = 50)
    private String educationLevel;

    @Column(name = "health_insurance", nullable = false, length = 100)
    private String healthInsurance;

    @Column(name = "ethnic_self_identification", length = 50)
    private String ethnicSelfIdentification;

    @Column(length = 50)
    private String religion;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "portal_user_id")
    private Long portalUserId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * New patient. The medical record number is the national ID, assigned here and never by the client.
     *
     * @throws BusinessRuleException if the birth date is after the admission date
     */
    public static Patient register(String nationalId, LocalDate admissionDate, PersonalData data, Long createdBy,
            Instant now) {
        Patient patient = new Patient();
        patient.nationalId = nationalId;
        patient.medicalRecordNumber = nationalId;
        patient.apply(admissionDate, data);
        patient.createdBy = createdBy;
        patient.createdAt = now;
        return patient;
    }

    /**
     * Replaces the admission date and the personal data (HU-06). The national ID and the medical record number
     * never change. Nothing is touched when the data is the same.
     *
     * @return whether something changed (and updatedBy/updatedAt were set)
     * @throws BusinessRuleException if the birth date is after the admission date
     */
    public boolean update(LocalDate admissionDate, PersonalData data, Long updatedBy, Instant now) {
        if (admissionDate.equals(this.admissionDate) && data.equals(personalData())) {
            return false;
        }
        apply(admissionDate, data);
        this.updatedBy = updatedBy;
        this.updatedAt = now;
        return true;
    }

    public PersonalData personalData() {
        return new PersonalData(firstName, lastName, birthDate, sex, phone, email, address, occupation,
                educationLevel, healthInsurance, ethnicSelfIdentification, religion);
    }

    private void apply(LocalDate admissionDate, PersonalData data) {
        if (data.birthDate().isAfter(admissionDate)) {
            throw new BusinessRuleException(BORN_AFTER_ADMISSION);
        }
        this.admissionDate = admissionDate;
        firstName = data.firstName();
        lastName = data.lastName();
        birthDate = data.birthDate();
        sex = data.sex();
        phone = data.phone();
        email = data.email();
        address = data.address();
        occupation = data.occupation();
        educationLevel = data.educationLevel();
        healthInsurance = data.healthInsurance();
        ethnicSelfIdentification = data.ethnicSelfIdentification();
        religion = data.religion();
    }

    /** Completed years on the given date. */
    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** Links the patient portal account; a patient has at most one. */
    public void linkPortalAccount(Long userId) {
        if (portalUserId != null) {
            throw new BusinessRuleException(ALREADY_LINKED);
        }
        portalUserId = userId;
    }

}
