package ec.rehabelite.clinicalrecords.application;

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
 * adds the clinical summary of each one with a single query per page.
 */
@Service
@RequiredArgsConstructor
public class PhysioPatientService {

    private final PatientDirectory patientDirectory;
    private final ClinicalRecordRepository clinicalRecordRepository;

    /** A patient with its clinical summary; {@code clinical} is null when the patient has no clinical record. */
    public record PhysioPatient(PatientCard patient, CurrentTreatmentView clinical) {
    }

    @Transactional(readOnly = true)
    public Page<PhysioPatient> search(String query, Pageable pageable) {
        Page<PatientCard> patients = patientDirectory.search(query, pageable);
        List<Long> ids = patients.getContent().stream().map(PatientCard::id).toList();
        Map<Long, CurrentTreatmentView> summaries = ids.isEmpty()
                ? Map.of()
                : clinicalRecordRepository.findCurrentTreatments(ids).stream()
                        .collect(Collectors.toMap(CurrentTreatmentView::getPatientId, Function.identity()));
        return patients.map(patient -> new PhysioPatient(patient, summaries.get(patient.id())));
    }

}
