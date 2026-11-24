package ec.rehabelite.staff.application;

import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Identity rules of a staff member (MEJ-02): the national ID is unique between patients and staff, and the email is
 * unique against patients, staff and accounts (as their email or as a pending new email). A conflict answers 409 with
 * the id of the existing record ({@code existingPatientId} or {@code existingStaffId}, when there is one) and the
 * field ({@code field}: {@code nationalId} or {@code email}); never the email itself.
 */
@Component
@RequiredArgsConstructor
class StaffIdentityRules {

    static final String PATIENT_NATIONAL_ID = "Esta cédula pertenece a un paciente registrado.";
    static final String STAFF_NATIONAL_ID = "Ya existe un miembro del personal registrado con esta cédula.";
    static final String PATIENT_EMAIL = "Este correo pertenece a un paciente registrado.";
    static final String STAFF_EMAIL = "Ya existe un miembro del personal registrado con este correo.";
    static final String ACCOUNT_EMAIL = "Ya existe una cuenta con ese correo electrónico.";
    static final String EXISTING_PATIENT_ID = "existingPatientId";
    static final String EXISTING_STAFF_ID = "existingStaffId";
    static final String FIELD = "field";

    private final StaffRecordRepository staffRepository;
    private final PatientDirectory patients;
    private final AccountProvisioning accounts;

    /** @param ownStaffId the record being edited, or null for a new one */
    void checkNationalId(String nationalId, Long ownStaffId) {
        staffRepository.findByNationalId(nationalId)
                .filter(existing -> !existing.getId().equals(ownStaffId))
                .ifPresent(existing -> {
                    throw conflict(STAFF_NATIONAL_ID, EXISTING_STAFF_ID, existing.getId(), "nationalId");
                });
        patients.findIdByNationalId(nationalId).ifPresent(patientId -> {
            throw conflict(PATIENT_NATIONAL_ID, EXISTING_PATIENT_ID, patientId, "nationalId");
        });
    }

    /**
     * @param email already normalized (trimmed, lower case)
     * @param ownStaffId the record being edited, or null for a new one
     * @param ownAccountId its account, or null for a new one
     */
    void checkEmail(String email, Long ownStaffId, Long ownAccountId) {
        List<Long> patientIds = patients.idsWithEmail(email);
        if (!patientIds.isEmpty()) {
            throw conflict(PATIENT_EMAIL, EXISTING_PATIENT_ID, patientIds.getFirst(), "email");
        }
        staffRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(ownStaffId))
                .ifPresent(existing -> {
                    throw conflict(STAFF_EMAIL, EXISTING_STAFF_ID, existing.getId(), "email");
                });
        Optional<AccountSummary> account = accounts.accountsUsingEmail(email).stream()
                .filter(other -> !Objects.equals(other.userId(), ownAccountId))
                .findFirst();
        if (account.isPresent()) {
            Long userId = account.get().userId();
            Optional<Long> staffOwner = staffRepository.findByUserId(userId).map(StaffRecord::getId);
            if (staffOwner.isPresent()) {
                throw conflict(ACCOUNT_EMAIL, EXISTING_STAFF_ID, staffOwner.get(), "email");
            }
            Optional<Long> patientOwner = patients.findByPortalUser(userId).map(PatientCard::id);
            throw patientOwner.map(id -> conflict(ACCOUNT_EMAIL, EXISTING_PATIENT_ID, id, "email"))
                    .orElseGet(() -> new ConflictException(ACCOUNT_EMAIL, Map.of(FIELD, "email")));
        }
    }

    private static ConflictException conflict(String message, String idProperty, Long id, String field) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(idProperty, id);
        properties.put(FIELD, field);
        return new ConflictException(message, properties);
    }

}
