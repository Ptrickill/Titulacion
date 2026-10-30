package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.CurrentTreatmentView;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The physiotherapist's patient list (HU-07): the patients module pages and searches the patients, and this module
 * adds the clinical summary of each one with a single query per page. The summary is clinical data, so each record
 * shown is audited as READ (HU-18).
 */
@Service
@RequiredArgsConstructor
public class PhysioPatientService {

    private final PatientDirectory patientDirectory;
    private final ClinicalRecordRepository clinicalRecordRepository;
    private final AuditLogService auditLogService;

    /** A patient with its clinical summary; {@code clinical} is null when the patient has no clinical record. */
    public record PhysioPatient(PatientCard patient, CurrentTreatmentView clinical) {
    }

    /** Not read-only: the audit entries are inserted in the same transaction. */
    @Transactional
    public Page<PhysioPatient> search(String query, Pageable pageable) {
        Page<PatientCard> patients = patientDirectory.search(query, pageable);
        List<Long> ids = patients.getContent().stream().map(PatientCard::id).toList();
        Map<Long, CurrentTreatmentView> summaries = ids.isEmpty()
                ? Map.of()
                : clinicalRecordRepository.findCurrentTreatments(ids).stream()
                        .collect(Collectors.toMap(CurrentTreatmentView::getPatientId, Function.identity()));
        auditLogService.recordAll(AuditAction.READ, ClinicalRecordService.CLINICAL_RECORD_ENTITY,
                summaries.values().stream().map(CurrentTreatmentView::getClinicalRecordId).toList());
        return patients.map(patient -> new PhysioPatient(patient, summaries.get(patient.id())));
    }

}
