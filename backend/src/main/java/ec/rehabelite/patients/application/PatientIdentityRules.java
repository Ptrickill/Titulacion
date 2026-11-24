package ec.rehabelite.patients.application;

import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.Role;
import ec.rehabelite.patients.RegisteredIdentities;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.ConflictException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Identity rules of a patient (MEJ-02):
 * <ul>
 * <li>the national ID is unique between patients and staff;</li>
 * <li>the email may be shared with other patients (e.g. a minor and a parent; the form only warns), but never with a
 * staff member nor with a staff account, as its email or as a pending new email;</li>
 * <li>a patient with a portal account cannot take an email that another account uses, because the account follows
 * the patient's email.</li>
 * </ul>
 * A conflict answers 409 with the id of the existing record ({@code existingPatientId} or {@code existingStaffId})
 * and the field ({@code field}: {@code nationalId} or {@code email}); never the email itself.
 */
@Component
@RequiredArgsConstructor
class PatientIdentityRules {

    static final String DUPLICATE_NATIONAL_ID = "Ya existe un paciente registrado con esta cédula.";
    static final String STAFF_NATIONAL_ID = "Esta cédula pertenece a un miembro del personal registrado.";
    static final String STAFF_EMAIL = "Este correo pertenece a un miembro del personal registrado.";
    static final String ACCOUNT_EMAIL = "Ya existe una cuenta con ese correo electrónico.";
    static final String EXISTING_PATIENT_ID = "existingPatientId";
    static final String EXISTING_STAFF_ID = "existingStaffId";
    static final String FIELD = "field";

    private final PatientRepository patientRepository;
    private final RegisteredIdentities registeredIdentities;
    private final AccountProvisioning accounts;

    void checkNationalId(String nationalId) {
        patientRepository.findByNationalId(nationalId).ifPresent(existing -> {
            throw new ConflictException(DUPLICATE_NATIONAL_ID,
                    Map.of(EXISTING_PATIENT_ID, existing.getId(), FIELD, "nationalId"));
        });
        registeredIdentities.staffWithNationalId(nationalId).ifPresent(staffId -> {
            throw new ConflictException(STAFF_NATIONAL_ID, Map.of(EXISTING_STAFF_ID, staffId, FIELD, "nationalId"));
        });
    }

    /**
     * @param email already normalized (trimmed, lower case)
     * @param ownAccountId the patient's portal account, or null
     */
    void checkEmail(String email, Long ownAccountId) {
        registeredIdentities.staffWithEmail(email).ifPresent(staffId -> {
            throw new ConflictException(STAFF_EMAIL, Map.of(EXISTING_STAFF_ID, staffId, FIELD, "email"));
        });
        List<AccountSummary> others = accounts.accountsUsingEmail(email).stream()
                .filter(account -> !Objects.equals(account.userId(), ownAccountId))
                .toList();
        boolean taken = ownAccountId != null
                ? !others.isEmpty()
                : others.stream().anyMatch(account -> account.role() != Role.PATIENT);
        if (taken) {
            throw new ConflictException(ACCOUNT_EMAIL, Map.of(FIELD, "email"));
        }
    }

}
