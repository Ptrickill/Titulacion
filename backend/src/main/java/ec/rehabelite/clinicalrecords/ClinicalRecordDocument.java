package ec.rehabelite.clinicalrecords;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole clinical record of a patient (MEJ-06), only for the export the physiotherapist hands to the data subject:
 * habits and medical history and every treatment with its full content, its responsibles and its status changes, the
 * oldest treatment first. Never used to show data on a screen: each view has its own minimal type.
 */
public record ClinicalRecordDocument(
        Long recordId,
        Long patientId,
        String habits,
        String physicalActivity,
        String allergies,
        String currentMedication,
        String personalHistory,
        String surgicalHistory,
        String familyHistory,
        Instant createdAt,
        Instant updatedAt,
        List<TreatmentDocument> treatments) {

    /** A treatment with its plan, its responsibles (the oldest first) and its status changes (the oldest first). */
    public record TreatmentDocument(
            Long id,
            String diagnosisCode,
            String diagnosisDescription,
            String chiefComplaint,
            String currentIllness,
            String referringPhysician,
            String complementaryTests,
            String severity,
            String painType,
            String painLocation,
            int initialPainScore,
            String painFrequency,
            String sessionFrequency,
            String goal,
            int plannedSessions,
            String status,
            LocalDate startDate,
            List<ResponsiblePeriod> responsibles,
            List<StatusChangeDocument> statusChanges) {
    }

    /** A physiotherapist responsible of the treatment from {@code validFrom} to {@code validTo} (null: still now). */
    public record ResponsiblePeriod(Long physiotherapistId, Instant validFrom, Instant validTo) {
    }

    /** A status change of the treatment with its reason. */
    public record StatusChangeDocument(String previousStatus, String newStatus, String reason, Long changedById,
            Instant changedAt) {
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "ClinicalRecordDocument[recordId=" + recordId + "]";
    }

}
