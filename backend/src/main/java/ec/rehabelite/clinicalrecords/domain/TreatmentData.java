package ec.rehabelite.clinicalrecords.domain;

/**
 * Initial assessment and therapy plan of a treatment: diagnostic information, pain assessment and plan. The ICD-10
 * diagnosis is resolved from the catalog by the service. Text is stored trimmed.
 */
public record TreatmentData(
        String chiefComplaint,
        String currentIllness,
        String referringPhysician,
        String complementaryTests,
        Severity severity,
        String painType,
        String painLocation,
        int initialPainScore,
        String painFrequency,
        String sessionFrequency,
        String goal,
        int plannedSessions) {

    public TreatmentData {
        chiefComplaint = ClinicalHistoryData.strip(chiefComplaint);
        currentIllness = ClinicalHistoryData.strip(currentIllness);
        referringPhysician = ClinicalHistoryData.strip(referringPhysician);
        complementaryTests = ClinicalHistoryData.strip(complementaryTests);
        painType = ClinicalHistoryData.strip(painType);
        painLocation = ClinicalHistoryData.strip(painLocation);
        painFrequency = ClinicalHistoryData.strip(painFrequency);
        sessionFrequency = ClinicalHistoryData.strip(sessionFrequency);
        goal = ClinicalHistoryData.strip(goal);
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "TreatmentData[...]";
    }

}
