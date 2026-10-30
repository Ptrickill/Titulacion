package ec.rehabelite.clinicalrecords.application;

import static java.util.stream.Collectors.toMap;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientProfile;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full clinical record of a patient before a session (HU-09): personal data, habits and medical history, the active
 * treatment with its current responsible and the previous treatments. The number of queries does not depend on the
 * number of treatments: the patient, the record with its treatments and diagnoses, the current responsibles and
 * their names, one query each. Every read is audited as READ of the clinical record.
 */
@Service
@RequiredArgsConstructor
public class ClinicalRecordViewService {

    /** Previous treatments: the most recent first (start date, then id). */
    private static final Comparator<Treatment> MOST_RECENT_FIRST =
            Comparator.comparing(Treatment::getStartDate).thenComparing(Treatment::getId).reversed();

    private static final Logger log = LoggerFactory.getLogger(ClinicalRecordViewService.class);

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final ResponsibleAssignmentRepository assignmentRepository;
    private final PatientDirectory patientDirectory;
    private final StaffDirectory staffDirectory;
    private final AuditLogService auditLogService;

    /**
     * The record as the physiotherapist reads it.
     *
     * @param activeTreatment     the ACTIVE treatment, or null when there is none
     * @param previousTreatments  the other treatments, the most recent first
     * @param currentResponsibles current assignment by treatment id
     * @param userNames           full names of the responsible physiotherapists by user id
     */
    public record ClinicalRecordView(PatientProfile patient, ClinicalRecord record, Treatment activeTreatment,
            List<Treatment> previousTreatments, Map<Long, ResponsibleAssignment> currentResponsibles,
            Map<Long, String> userNames) {

        public Optional<ResponsibleAssignment> responsibleOf(Treatment treatment) {
            return Optional.ofNullable(currentResponsibles.get(treatment.getId()));
        }

        public String nameOf(Long userId) {
            return userNames.get(userId);
        }

    }

    /**
     * @throws ResourceNotFoundException if the patient does not exist or has no clinical record (not audited)
     */
    @Transactional
    public ClinicalRecordView view(Long patientId) {
        PatientProfile patient = patientDirectory.findProfile(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(ClinicalRecordService.PATIENT_NOT_FOUND));
        ClinicalRecord record = clinicalRecordRepository.findWithTreatmentsByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(ClinicalRecordService.PATIENT_WITHOUT_CLINICAL_RECORD));

        List<Treatment> treatments = record.getTreatments();
        Map<Long, ResponsibleAssignment> currentResponsibles = treatments.isEmpty()
                ? Map.of()
                : assignmentRepository.findCurrentByTreatmentIds(treatments.stream().map(Treatment::getId).toList())
                        .stream()
                        .collect(toMap(assignment -> assignment.getTreatment().getId(), Function.identity()));
        Map<Long, String> userNames = staffDirectory.fullNames(currentResponsibles.values().stream()
                .map(ResponsibleAssignment::getPhysiotherapistId)
                .distinct()
                .toList());

        Treatment active = treatments.stream().filter(Treatment::isActive).findFirst().orElse(null);
        List<Treatment> previous = treatments.stream()
                .filter(treatment -> !treatment.isActive())
                .sorted(MOST_RECENT_FIRST)
                .toList();

        auditLogService.record(AuditAction.READ, ClinicalRecordService.CLINICAL_RECORD_ENTITY, record.getId());
        log.debug("Clinical record read clinicalRecordId={} treatments={}", record.getId(), treatments.size());
        return new ClinicalRecordView(patient, record, active, previous, currentResponsibles, userNames);
    }

}
