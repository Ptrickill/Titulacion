package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.PortalAccountCreated;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient side of the portal accounts (HU-02): which patients can receive one, and the link itself.
 */
@Service
@RequiredArgsConstructor
public class PortalAccountService {

    static final String AUDITED_ENTITY = "Patient";
    static final String PATIENT_NOT_FOUND = "El paciente seleccionado no existe.";
    static final int MAX_CANDIDATES = 20;

    private static final Logger log = LoggerFactory.getLogger(PortalAccountService.class);

    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;

    /** Up to 20 patients without a portal account matching the name or national ID prefix. */
    @Transactional(readOnly = true)
    public List<Patient> findCandidates(String query) {
        return patientRepository.findPortalCandidates(sanitize(query), MAX_CANDIDATES);
    }

    /**
     * Runs synchronously inside the account creation transaction, so any exception here also rolls back
     * the new account.
     */
    @EventListener
    @Transactional
    public void on(PortalAccountCreated event) {
        Patient patient = patientRepository.findById(event.patientId())
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
        patient.linkPortalAccount(event.userId());
        auditLogService.record(AuditAction.UPDATE, AUDITED_ENTITY, patient.getId());
        log.info("Portal account linked patientId={} userId={}", patient.getId(), event.userId());
    }

    /** LIKE wildcards typed by the user are removed so they match literally nothing special. */
    private static String sanitize(String query) {
        return query == null ? "" : query.strip().replaceAll("[%_\\\\]", "");
    }

}
