package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.application.ClinicalRecordViewService.ClinicalRecordView;
import ec.rehabelite.clinicalrecords.application.PhysioPatientService.PhysioPatient;
import ec.rehabelite.clinicalrecords.application.TreatmentService.TreatmentHistory;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.CurrentTreatmentView;
import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChange;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ActiveTreatmentResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ClinicalHistoryResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ClinicalRecordResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ClinicalRecordViewResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.CurrentTreatmentResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.Icd10DiagnosisResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PatientProfileResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PhysioPatientResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PhysiotherapistResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PreviousTreatmentResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ResponsibleResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.StatusChangeResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.TreatmentHistoryResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.TreatmentResponse;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientProfile;
import java.time.LocalDate;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface ClinicalRecordWebMapper {

    Icd10DiagnosisResponse toResponse(Icd10Diagnosis diagnosis);

    List<Icd10DiagnosisResponse> toDiagnosisResponses(List<Icd10Diagnosis> diagnoses);

    @Mapping(target = "clinicalRecordId", expression = "java(treatment.getClinicalRecord().getId())")
    @Mapping(target = "patientId", expression = "java(treatment.getClinicalRecord().getPatientId())")
    @Mapping(target = "responsiblePhysiotherapistId", expression = """
            java(treatment.currentResponsible().map(a -> a.getPhysiotherapistId()).orElse(null))""")
    TreatmentResponse toResponse(Treatment treatment);

    ClinicalRecordResponse toResponse(ClinicalRecord record);

    PhysiotherapistResponse toResponse(StaffMember physiotherapist);

    List<PhysiotherapistResponse> toPhysiotherapistResponses(List<StaffMember> physiotherapists);

    /** The current responsible is taken from the history: it is the assignment without an end. */
    default TreatmentHistoryResponse toResponse(TreatmentHistory history) {
        Treatment treatment = history.treatment();
        List<ResponsibleResponse> responsibles = history.responsibles().stream()
                .map(assignment -> responsibleOf(assignment, history))
                .toList();
        ResponsibleResponse current = history.responsibles().stream()
                .filter(ResponsibleAssignment::isCurrent)
                .findFirst()
                .map(assignment -> responsibleOf(assignment, history))
                .orElse(null);
        List<StatusChangeResponse> statusChanges = history.statusChanges().stream()
                .map(change -> statusChangeOf(change, history))
                .toList();
        return new TreatmentHistoryResponse(treatment.getId(), treatment.getStatus(),
                treatment.allowedNextStatuses(), current, responsibles, statusChanges);
    }

    private static ResponsibleResponse responsibleOf(ResponsibleAssignment assignment, TreatmentHistory history) {
        return new ResponsibleResponse(assignment.getPhysiotherapistId(),
                history.nameOf(assignment.getPhysiotherapistId()), assignment.getValidFrom(),
                assignment.getValidTo());
    }

    private static StatusChangeResponse statusChangeOf(TreatmentStatusChange change, TreatmentHistory history) {
        return new StatusChangeResponse(change.getId(), change.getPreviousStatus(), change.getNewStatus(),
                change.getReason(), change.getUserId(), history.nameOf(change.getUserId()), change.getChangedAt());
    }

    ClinicalHistoryResponse toHistoryResponse(ClinicalRecord record);

    /** The age is calculated on the given date (today in the clinic); it is never stored. */
    default ClinicalRecordViewResponse toResponse(ClinicalRecordView view, LocalDate today) {
        PatientProfile patient = view.patient();
        PatientProfileResponse patientResponse = new PatientProfileResponse(patient.id(), patient.nationalId(),
                patient.medicalRecordNumber(), patient.firstName(), patient.lastName(), patient.birthDate(),
                patient.ageOn(today), patient.sex(), patient.phone(), patient.email(), patient.address(),
                patient.occupation(), patient.educationLevel(), patient.healthInsurance(), patient.admissionDate());
        Treatment active = view.activeTreatment();
        ActiveTreatmentResponse activeResponse = active == null ? null : new ActiveTreatmentResponse(active.getId(),
                toResponse(active.getDiagnosis()), active.getChiefComplaint(), active.getCurrentIllness(),
                active.getReferringPhysician(), active.getComplementaryTests(), active.getSeverity(),
                active.getPainType(), active.getPainLocation(), active.getInitialPainScore(),
                active.getPainFrequency(), active.getSessionFrequency(), active.getGoal(),
                active.getPlannedSessions(), active.getStatus(), active.getStartDate(),
                currentResponsibleOf(active, view));
        List<PreviousTreatmentResponse> previous = view.previousTreatments().stream()
                .map(treatment -> new PreviousTreatmentResponse(treatment.getId(),
                        toResponse(treatment.getDiagnosis()), treatment.getSeverity(), treatment.getStatus(),
                        treatment.getStartDate(), treatment.getPlannedSessions(), treatment.getUpdatedAt(),
                        currentResponsibleOf(treatment, view)))
                .toList();
        return new ClinicalRecordViewResponse(patientResponse, toHistoryResponse(view.record()), activeResponse,
                previous);
    }

    private static ResponsibleResponse currentResponsibleOf(Treatment treatment, ClinicalRecordView view) {
        return view.responsibleOf(treatment)
                .map(assignment -> new ResponsibleResponse(assignment.getPhysiotherapistId(),
                        view.nameOf(assignment.getPhysiotherapistId()), assignment.getValidFrom(),
                        assignment.getValidTo()))
                .orElse(null);
    }

    /** The age is calculated on the given date (today in the clinic); it is never stored. */
    default PhysioPatientResponse toResponse(PhysioPatient row, LocalDate today) {
        PatientCard patient = row.patient();
        CurrentTreatmentView clinical = row.clinical();
        CurrentTreatmentResponse current = clinical == null || clinical.getTreatmentId() == null
                ? null
                : new CurrentTreatmentResponse(clinical.getTreatmentId(), clinical.getDiagnosisCode(),
                        clinical.getDiagnosisDescription(), Severity.valueOf(clinical.getSeverity()),
                        clinical.getPlannedSessions(), TreatmentStatus.valueOf(clinical.getStatus()));
        return new PhysioPatientResponse(patient.id(), patient.nationalId(), patient.medicalRecordNumber(),
                patient.firstName(), patient.lastName(), patient.ageOn(today),
                clinical == null ? null : clinical.getClinicalRecordId(), current);
    }

}
