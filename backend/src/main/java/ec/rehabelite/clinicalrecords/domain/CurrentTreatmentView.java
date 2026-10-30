package ec.rehabelite.clinicalrecords.domain;

/**
 * Clinical summary of a patient for the physiotherapist's list: the record and its current treatment (the active
 * one, otherwise the most recent). Treatment columns are null when the record has no treatment.
 */
public interface CurrentTreatmentView {

    Long getPatientId();

    Long getClinicalRecordId();

    Long getTreatmentId();

    String getDiagnosisCode();

    String getDiagnosisDescription();

    String getSeverity();

    Integer getPlannedSessions();

    String getStatus();

}
