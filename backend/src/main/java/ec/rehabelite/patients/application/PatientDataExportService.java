package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.catalogs.AddressFormatter;
import ec.rehabelite.patients.ConsentChannel;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.domain.DataRequestStatus;
import ec.rehabelite.patients.domain.DataRequestType;
import ec.rehabelite.patients.domain.DataSubjectRequestRepository;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientConsentRepository;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export of the personal data of a patient (MEJ-06) that the reception hands to the data subject (rights of access and
 * portability): personal and contact data, the portal account, the consents and the requests. Never clinical data:
 * the physiotherapist exports the clinical record from the record view. Audited as EXPORT Patient.
 */
@Service
@RequiredArgsConstructor
public class PatientDataExportService {

    private static final Logger log = LoggerFactory.getLogger(PatientDataExportService.class);

    private final PatientRepository patientRepository;
    private final PatientConsentRepository consentRepository;
    private final DataSubjectRequestRepository requestRepository;
    private final AccountProvisioning accounts;
    private final AddressFormatter addressFormatter;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** The export: who generated it and when, and the patient's personal data, account, consents and requests. */
    public record PatientDataExport(Instant generatedAt, String generatedBy, PersonalDataExport patient,
            PortalAccountExport portalAccount, List<ConsentExport> consents, List<RequestExport> requests) {

        /** Never print personal data, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "PatientDataExport[...]";
        }

    }

    /** Admission, personal and contact data; email, phone and address are null after an accepted erasure. */
    public record PersonalDataExport(Long id, String nationalId, String medicalRecordNumber, String firstName,
            String lastName, LocalDate birthDate, String sex, String phone, String email, String address,
            String occupation, String educationLevel, boolean hasHealthInsurance, String ethnicSelfIdentification,
            String religion, LocalDate admissionDate, Instant registeredAt, Instant blockedAt) {
    }

    /** The portal account: its status and last access, never its credentials. Null when the patient has none. */
    public record PortalAccountExport(String status, Instant lastLoginAt) {
    }

    public record ConsentExport(ConsentType type, String textVersion, ConsentChannel channel, Instant grantedAt,
            Instant replacedAt, Instant revokedAt, ConsentChannel revocationChannel) {
    }

    public record RequestExport(DataRequestType type, LocalDate receivedOn, DataRequestStatus status, String response,
            Instant resolvedAt) {
    }

    /**
     * The export of the patient's personal data, audited as EXPORT Patient.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     */
    @Transactional
    public PatientDataExport export(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
        String address = addressFormatter.format(patient.getAddress());
        PersonalDataExport personal = new PersonalDataExport(patient.getId(), patient.getNationalId(),
                patient.getMedicalRecordNumber(), patient.getFirstName(), patient.getLastName(), patient.getBirthDate(),
                patient.getSex().name(), patient.getPhone(), patient.getEmail(), address.isEmpty() ? null : address,
                patient.getOccupation(), patient.getEducationLevel(), patient.isHasHealthInsurance(),
                patient.getEthnicSelfIdentification(), patient.getReligion(), patient.getAdmissionDate(),
                patient.getCreatedAt(), patient.getBlockedAt());
        PortalAccountExport account = accounts.summary(patient.getPortalUserId())
                .map(summary -> new PortalAccountExport(summary.status().name(), summary.lastLoginAt()))
                .orElse(null);
        List<ConsentExport> consents = consentRepository.findByPatientIdOrderByGrantedAtDescIdDesc(patientId).stream()
                .map(c -> new ConsentExport(c.getType(), c.getTextVersion(), c.getChannel(), c.getGrantedAt(),
                        c.getReplacedAt(), c.getRevokedAt(), c.getRevocationChannel()))
                .toList();
        List<RequestExport> requests = requestRepository.findByPatientIdOrderByReceivedOnDescIdDesc(patientId).stream()
                .map(r -> new RequestExport(r.getType(), r.getReceivedOn(), r.getStatus(), r.getResponse(),
                        r.getResolvedAt()))
                .toList();

        auditLogService.record(AuditAction.EXPORT, PatientRegistrationService.AUDITED_ENTITY, patient.getId());
        log.info("Patient personal data exported patientId={}", patient.getId());
        return new PatientDataExport(clock.instant(),
                currentUserProvider.currentUser().map(CurrentUser::fullName).orElse(null), personal, account, consents,
                requests);
    }

    /**
     * The same export as a PDF, built inside the transaction: if it fails, no audit entry is left.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     */
    @Transactional
    public byte[] exportPdf(Long patientId) {
        return PatientDataPdf.render(export(patientId));
    }

}
