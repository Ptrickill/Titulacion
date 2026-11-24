package ec.rehabelite.staff.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.PhoneNumber;
import ec.rehabelite.shared.domain.PostalAddress;
import ec.rehabelite.shared.domain.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Personal data of a staff member (MEJ-02), linked to exactly one account ({@code user_id}, unique). The role lives
 * in the account. A record created for an account that existed before MEJ-02 (or for the initial administrator) has
 * only the names and the email: the rest is to be completed ({@link #needsCompletion()}) and is required when the
 * record is edited. The national ID never changes once it is set.
 */
@Entity
@Table(name = "staff_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffRecord {

    public static final String NATIONAL_ID_FIXED = "La cédula registrada no se puede modificar.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Null only for a record still to be completed. */
    @Column(name = "national_id", length = 10)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String nationalId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 254)
    private String email;

    @Embedded
    private PostalAddress address;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** New staff member registered in the people registry, with its new account. */
    public static StaffRecord register(String nationalId, StaffPersonalData data, Long userId, Long createdBy,
            Instant now) {
        StaffRecord record = new StaffRecord();
        record.userId = userId;
        record.nationalId = nationalId;
        record.apply(data);
        record.createdBy = createdBy;
        record.createdAt = now;
        return record;
    }

    /**
     * Record of an account created outside the registry (the initial administrator): only its names, split from the
     * account name ({@link NameSplit}), and its email. Everything else is to be completed.
     */
    public static StaffRecord forExistingAccount(Long userId, String email, String fullName, Instant now) {
        String[] names = NameSplit.of(fullName);
        StaffRecord record = new StaffRecord();
        record.userId = userId;
        record.firstName = names[0];
        record.lastName = names[1];
        record.email = email;
        record.createdAt = now;
        return record;
    }

    /**
     * Replaces the personal data; sets the national ID of a record that had none. Nothing is touched when the data is
     * the same.
     *
     * @return whether something changed (and updatedBy/updatedAt were set)
     * @throws BusinessRuleException if the record already has a different national ID
     */
    public boolean update(String nationalId, StaffPersonalData data, Long updatedBy, Instant now) {
        if (this.nationalId != null && !this.nationalId.equals(nationalId)) {
            throw new BusinessRuleException(NATIONAL_ID_FIXED);
        }
        // personalData() capitalizes the stored names again: names kept as written must still count as a change
        if (Objects.equals(this.nationalId, nationalId) && data.equals(personalData())
                && data.firstName().equals(firstName) && data.lastName().equals(lastName)) {
            return false;
        }
        this.nationalId = nationalId;
        apply(data);
        this.updatedBy = updatedBy;
        this.updatedAt = now;
        return true;
    }

    public StaffPersonalData personalData() {
        return new StaffPersonalData(firstName, lastName, birthDate, sex, phone, email, address);
    }

    /** "First names last names": the account name (MEJ-02). */
    public String fullName() {
        return (firstName + " " + lastName).strip();
    }

    /**
     * Data to complete: a record of an account created before MEJ-02 (no national ID, birth date, sex, address or
     * last name) or a phone outside the rule of {@link PhoneNumber}.
     */
    public boolean needsCompletion() {
        return nationalId == null || birthDate == null || sex == null || lastName.isBlank()
                || !PhoneNumber.isValid(phone) || !PostalAddress.isComplete(address);
    }

    /** Completed years on the given date; null without a birth date. */
    public Integer ageOn(LocalDate date) {
        return birthDate == null ? null : Period.between(birthDate, date).getYears();
    }

    private void apply(StaffPersonalData data) {
        firstName = data.firstName();
        lastName = data.lastName();
        birthDate = data.birthDate();
        sex = data.sex();
        phone = data.phone();
        email = data.email();
        address = data.address();
    }

}
