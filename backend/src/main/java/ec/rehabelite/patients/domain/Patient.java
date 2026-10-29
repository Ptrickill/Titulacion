package ec.rehabelite.patients.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registered patient. Only the identification fields and the portal account link are mapped so far;
 * the patient registration story (HU-05) maps the rest of the columns.
 */
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    public static final String ALREADY_LINKED = "El paciente seleccionado ya tiene una cuenta de portal.";

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

    @Column(name = "portal_user_id")
    private Long portalUserId;

    /** Links the patient portal account; a patient has at most one. */
    public void linkPortalAccount(Long userId) {
        if (portalUserId != null) {
            throw new BusinessRuleException(ALREADY_LINKED);
        }
        portalUserId = userId;
    }

}
