package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.domain.DataRequestStatus;
import ec.rehabelite.patients.domain.DataRequestType;
import ec.rehabelite.patients.domain.DataSubjectRequest;
import ec.rehabelite.patients.domain.DataSubjectRequestRepository;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requests of the data subject (MEJ-06): the reception registers them and follows them up to their response. An
 * erasure request is accepted only by the operations director, who confirms it by typing the patient's national ID;
 * accepting it blocks the patient, clears its email, phone and address, revokes its consents and anonymizes its portal
 * account, while the clinical record is kept. Every change is audited and locks the patient row.
 */
@Service
@RequiredArgsConstructor
public class DataSubjectRequestService {

    static final String AUDITED_ENTITY = "DataSubjectRequest";
    static final String REQUEST_NOT_FOUND = "La solicitud no existe.";
    static final String RECEIVED_IN_THE_FUTURE = "La fecha de recepción no puede ser posterior a hoy.";
    static final String ERASURE_NEEDS_DIRECTOR = "Solo la dirección operativa puede aceptar una solicitud de "
            + "eliminación.";
    static final String NOT_AN_ERASURE = "La solicitud no es de eliminación.";
    static final String NATIONAL_ID_MISMATCH = "La cédula ingresada no coincide con la del paciente.";
    static final String ALREADY_BLOCKED = "Los datos del paciente ya están bloqueados.";
    /** Domain that receives nothing (RFC 2606 reserves .invalid), for the anonymized portal account. */
    static final String ANONYMIZED_EMAIL_DOMAIN = "@invalid.rehabelite";

    private static final Logger log = LoggerFactory.getLogger(DataSubjectRequestService.class);

    private final DataSubjectRequestRepository requestRepository;
    private final PatientRepository patientRepository;
    private final ConsentService consentService;
    private final AccountProvisioning accounts;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final AppProperties properties;
    private final Clock clock;

    /** A request with its deadline: the day it is due and the days since it was received, in Quito. */
    public record TrackedRequest(DataSubjectRequest request, LocalDate dueOn, long daysElapsed, boolean overdue) {
    }

    /** An open erasure request with the patient's name and national ID, for the operations director. */
    public record ErasureRequest(TrackedRequest tracked, String patientName, String nationalId) {
    }

    /** The patient's requests, the most recently received first. */
    @Transactional(readOnly = true)
    public List<TrackedRequest> list(Long patientId) {
        findPatient(patientId);
        return requestRepository.findByPatientIdOrderByReceivedOnDescIdDesc(patientId).stream().map(this::track)
                .toList();
    }

    /**
     * Registers a request received on the given day (today at the latest). Audited as CREATE DataSubjectRequest.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws InvalidRequestException if the day is after today
     */
    @Transactional
    public TrackedRequest register(Long patientId, DataRequestType type, LocalDate receivedOn) {
        Patient patient = lockPatient(patientId);
        if (receivedOn.isAfter(ClinicCalendar.today(clock))) {
            throw new InvalidRequestException(RECEIVED_IN_THE_FUTURE);
        }
        Long userId = currentUserProvider.currentUserId().orElse(null);
        DataSubjectRequest request = requestRepository.save(DataSubjectRequest.register(patient.getId(), type,
                receivedOn, userId, clock.instant()));
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, request.getId());
        log.info("Data subject request registered requestId={} patientId={} type={}", request.getId(),
                patient.getId(), type);
        return track(request);
    }

    /**
     * The reception moves a request of the patient forward: in progress, resolved or rejected with a response. It
     * never resolves an erasure request: only the operations director accepts one. Audited as STATUS_CHANGE
     * DataSubjectRequest when the status changes.
     *
     * @throws ResourceNotFoundException if the patient or the request (of that patient) does not exist
     * @throws BusinessRuleException if the change is not allowed
     */
    @Transactional
    public TrackedRequest changeStatus(Long patientId, Long requestId, DataRequestStatus status, String response) {
        lockPatient(patientId);
        DataSubjectRequest request = findRequest(requestId);
        if (!request.getPatientId().equals(patientId)) {
            throw new ResourceNotFoundException(REQUEST_NOT_FOUND);
        }
        if (request.getType() == DataRequestType.ERASURE && status == DataRequestStatus.RESOLVED) {
            throw new BusinessRuleException(ERASURE_NEEDS_DIRECTOR);
        }
        applyStatus(request, status, response);
        return track(request);
    }

    /** The open erasure requests, the oldest first, with the patient's name and national ID. */
    @Transactional(readOnly = true)
    public List<ErasureRequest> openErasureRequests() {
        List<DataSubjectRequest> requests = requestRepository.findOpenByType(DataRequestType.ERASURE);
        Map<Long, Patient> patients = patientRepository.findAllById(requests.stream()
                .map(DataSubjectRequest::getPatientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        return requests.stream().map(request -> {
            Patient patient = patients.get(request.getPatientId());
            return new ErasureRequest(track(request), patient.fullName(), patient.getNationalId());
        }).toList();
    }

    /**
     * The operations director accepts an erasure request, confirming it with the patient's national ID: the patient
     * is blocked and its email, phone and address cleared (ANONYMIZATION Patient), its consents revoked, its portal
     * account deactivated and anonymized (ANONYMIZATION User), and the request resolved with the response
     * (STATUS_CHANGE DataSubjectRequest). The clinical record is kept, readable by the physiotherapists. Irreversible.
     *
     * @throws ResourceNotFoundException if the request does not exist
     * @throws BusinessRuleException if it is not an open erasure request, the national ID does not match, the
     *         response is blank or the patient is already blocked
     */
    @Transactional
    public TrackedRequest acceptErasure(Long requestId, String nationalId, String response) {
        DataSubjectRequest request = findErasure(requestId);
        Patient patient = lockPatient(request.getPatientId());
        if (!patient.getNationalId().equals(nationalId == null ? null : nationalId.strip())) {
            throw new BusinessRuleException(NATIONAL_ID_MISMATCH);
        }
        if (patient.isBlocked()) {
            throw new BusinessRuleException(ALREADY_BLOCKED);
        }
        if (response == null || response.isBlank()) {
            throw new BusinessRuleException(DataSubjectRequest.RESPONSE_REQUIRED);
        }

        Long userId = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        patient.blockAndClearContactData(userId, now);
        auditLogService.record(AuditAction.ANONYMIZATION, PatientRegistrationService.AUDITED_ENTITY, patient.getId());
        consentService.revokeAllForErasure(patient);
        if (patient.getPortalUserId() != null) {
            accounts.anonymize(patient.getPortalUserId(),
                    "borrado+" + patient.getPortalUserId() + ANONYMIZED_EMAIL_DOMAIN);
        }
        applyStatus(request, DataRequestStatus.RESOLVED, response);
        log.info("Erasure request accepted requestId={} patientId={} by={}", request.getId(), patient.getId(), userId);
        return track(request);
    }

    /**
     * The operations director rejects an erasure request with a response. Audited as STATUS_CHANGE
     * DataSubjectRequest.
     *
     * @throws ResourceNotFoundException if the request does not exist
     * @throws BusinessRuleException if it is not an open erasure request or the response is blank
     */
    @Transactional
    public TrackedRequest rejectErasure(Long requestId, String response) {
        DataSubjectRequest request = findErasure(requestId);
        lockPatient(request.getPatientId());
        applyStatus(request, DataRequestStatus.REJECTED, response);
        return track(request);
    }

    private void applyStatus(DataSubjectRequest request, DataRequestStatus status, String response) {
        Long userId = currentUserProvider.currentUserId().orElse(null);
        if (request.changeStatus(status, response, userId, clock.instant())) {
            auditLogService.record(AuditAction.STATUS_CHANGE, AUDITED_ENTITY, request.getId());
            log.info("Data subject request status changed requestId={} status={} by={}", request.getId(), status,
                    userId);
        }
    }

    private TrackedRequest track(DataSubjectRequest request) {
        LocalDate today = ClinicCalendar.today(clock);
        LocalDate dueOn = request.getReceivedOn().plusDays(properties.compliance().requestResponseDays());
        long elapsed = ChronoUnit.DAYS.between(request.getReceivedOn(), today);
        return new TrackedRequest(request, dueOn, elapsed, request.isOpen() && today.isAfter(dueOn));
    }

    private DataSubjectRequest findErasure(Long requestId) {
        DataSubjectRequest request = findRequest(requestId);
        if (request.getType() != DataRequestType.ERASURE) {
            throw new BusinessRuleException(NOT_AN_ERASURE);
        }
        if (!request.isOpen()) {
            throw new BusinessRuleException(DataSubjectRequest.ALREADY_CLOSED);
        }
        return request;
    }

    private DataSubjectRequest findRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(REQUEST_NOT_FOUND));
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
    }

    private Patient lockPatient(Long patientId) {
        return patientRepository.findForUpdate(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
    }

}
