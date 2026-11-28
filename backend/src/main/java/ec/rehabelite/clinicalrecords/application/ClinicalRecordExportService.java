package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.clinicalrecords.ClinicalRecordDocument;
import ec.rehabelite.clinicalrecords.ClinicalRecordDocument.ResponsiblePeriod;
import ec.rehabelite.clinicalrecords.ClinicalRecordDocument.StatusChangeDocument;
import ec.rehabelite.clinicalrecords.ClinicalRecordDocument.TreatmentDocument;
import ec.rehabelite.clinicalrecords.ClinicalRecordExportPort;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChange;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChangeRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ClinicalRecordExportPort} (MEJ-06): the record with its treatments and diagnoses, every assignment and every
 * status change, in three reads whatever the number of treatments.
 */
@Service
@RequiredArgsConstructor
class ClinicalRecordExportService implements ClinicalRecordExportPort {

    private final ClinicalRecordRepository recordRepository;
    private final ResponsibleAssignmentRepository assignmentRepository;
    private final TreatmentStatusChangeRepository statusChangeRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ClinicalRecordDocument> documentOf(Long patientId) {
        return recordRepository.findWithTreatmentsByPatientId(patientId).map(this::document);
    }

    private ClinicalRecordDocument document(ClinicalRecord record) {
        List<Treatment> treatments = record.getTreatments().stream()
                .sorted(Comparator.comparing(Treatment::getStartDate).thenComparing(Treatment::getId))
                .toList();
        List<Long> ids = treatments.stream().map(Treatment::getId).toList();
        Map<Long, List<ResponsibleAssignment>> assignments = ids.isEmpty() ? Map.of()
                : assignmentRepository.findAllByTreatmentIds(ids).stream()
                        .collect(Collectors.groupingBy(a -> a.getTreatment().getId()));
        Map<Long, List<TreatmentStatusChange>> changes = ids.isEmpty() ? Map.of()
                : statusChangeRepository.findAllByTreatmentIds(ids).stream()
                        .collect(Collectors.groupingBy(c -> c.getTreatment().getId()));
        return new ClinicalRecordDocument(record.getId(), record.getPatientId(), record.getHabits(),
                record.getPhysicalActivity(), record.getAllergies(), record.getCurrentMedication(),
                record.getPersonalHistory(), record.getSurgicalHistory(), record.getFamilyHistory(),
                record.getCreatedAt(), record.getUpdatedAt(), treatments.stream()
                        .map(t -> treatment(t, assignments.getOrDefault(t.getId(), List.of()),
                                changes.getOrDefault(t.getId(), List.of())))
                        .toList());
    }

    private static TreatmentDocument treatment(Treatment t, List<ResponsibleAssignment> assignments,
            List<TreatmentStatusChange> changes) {
        return new TreatmentDocument(t.getId(), t.getDiagnosis().getCode(), t.getDiagnosis().getDescription(),
                t.getChiefComplaint(), t.getCurrentIllness(), t.getReferringPhysician(), t.getComplementaryTests(),
                t.getSeverity().name(), t.getPainType(), t.getPainLocation(), t.getInitialPainScore(),
                t.getPainFrequency(), t.getSessionFrequency(), t.getGoal(), t.getPlannedSessions(),
                t.getStatus().name(), t.getStartDate(),
                assignments.stream()
                        .map(a -> new ResponsiblePeriod(a.getPhysiotherapistId(), a.getValidFrom(), a.getValidTo()))
                        .toList(),
                changes.stream()
                        .map(c -> new StatusChangeDocument(c.getPreviousStatus().name(), c.getNewStatus().name(),
                                c.getReason(), c.getUserId(), c.getChangedAt()))
                        .toList());
    }

}
