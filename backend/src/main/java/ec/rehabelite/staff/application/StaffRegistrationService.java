package ec.rehabelite.staff.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import ec.rehabelite.staff.domain.StaffPersonalData;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and update of staff records (MEJ-02). A staff member and its account are created in one transaction,
 * with the email and the names of the record: the account name is always "first names last names", and editing the
 * email changes the account's email too.
 */
@Service
@RequiredArgsConstructor
public class StaffRegistrationService {

    static final String AUDITED_ENTITY = "StaffRecord";
    static final String STAFF_NOT_FOUND = "El registro del personal no existe.";
    static final String PATIENT_ROLE = "El rol Paciente no corresponde al personal.";

    private static final Logger log = LoggerFactory.getLogger(StaffRegistrationService.class);

    private final StaffRecordRepository staffRepository;
    private final StaffIdentityRules identityRules;
    private final AccountProvisioning accounts;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Creates the staff record and its account (pending activation) with the record's email and names, and emails
     * the activation link (MEJ-01), last. Audited as CREATE User, CREATE StaffRecord and INVITATION_SENT User.
     *
     * @throws ec.rehabelite.shared.domain.ConflictException if the national ID or the email is already registered
     *         (see {@link StaffIdentityRules})
     * @throws ec.rehabelite.shared.domain.ExternalServiceException if the email cannot be sent; nothing is created
     */
    @Transactional
    public StaffRecord register(RegisterStaffCommand command) {
        if (command.role() == Role.PATIENT) {
            throw new InvalidRequestException(PATIENT_ROLE);
        }
        StaffPersonalData data = command.personalData();
        identityRules.checkNationalId(command.nationalId(), null);
        identityRules.checkEmail(data.email(), null, null);

        Long createdBy = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        Long userId = accounts.create(data.email(), data.fullName(), command.role());
        StaffRecord record = staffRepository.save(
                StaffRecord.register(command.nationalId(), data, userId, createdBy, now));
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, record.getId());
        accounts.sendInvitation(userId);
        log.info("Staff member registered staffId={} userId={} role={} createdBy={}", record.getId(), userId,
                command.role(), createdBy);
        return record;
    }

    /**
     * Replaces the personal data (and sets the national ID of a record that had none). The account takes the
     * record's names, and a new email starts the account's change of email (see
     * {@link AccountProvisioning#changeEmail}). Sending the same data changes nothing and audits nothing.
     *
     * @throws ResourceNotFoundException if the record does not exist
     * @throws ec.rehabelite.shared.domain.ConflictException if the national ID or the email is already registered
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the national ID differs from the registered one,
     *         or the email changes while the account is deactivated
     * @throws ec.rehabelite.shared.domain.ExternalServiceException if the link to the new email cannot be sent
     */
    @Transactional
    public StaffRecord update(Long staffId, String nationalId, StaffPersonalData data) {
        StaffRecord record = find(staffId);
        if (record.getNationalId() == null) {
            identityRules.checkNationalId(nationalId, record.getId());
        }
        boolean emailChanged = !data.email().equals(record.getEmail());
        if (emailChanged) {
            identityRules.checkEmail(data.email(), record.getId(), record.getUserId());
        }

        Long updatedBy = currentUserProvider.currentUserId().orElse(null);
        if (record.update(nationalId, data, updatedBy, clock.instant())) {
            auditLogService.record(AuditAction.UPDATE, AUDITED_ENTITY, record.getId());
            log.info("Staff record updated staffId={} updatedBy={}", record.getId(), updatedBy);
        }
        accounts.rename(record.getUserId(), record.fullName());
        if (emailChanged) {
            accounts.changeEmail(record.getUserId(), record.getEmail());
        }
        return record;
    }

    @Transactional(readOnly = true)
    public StaffRecord find(Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException(STAFF_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<AccountSummary> accountOf(StaffRecord record) {
        return accounts.summary(record.getUserId());
    }

}
