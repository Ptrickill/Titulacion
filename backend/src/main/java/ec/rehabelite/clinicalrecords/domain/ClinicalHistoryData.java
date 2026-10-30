package ec.rehabelite.clinicalrecords.domain;

/** Habits and medical history of the clinical record; stored trimmed. */
public record ClinicalHistoryData(
        String habits,
        String physicalActivity,
        String allergies,
        String currentMedication,
        String personalHistory,
        String surgicalHistory,
        String familyHistory) {

    public ClinicalHistoryData {
        habits = strip(habits);
        physicalActivity = strip(physicalActivity);
        allergies = strip(allergies);
        currentMedication = strip(currentMedication);
        personalHistory = strip(personalHistory);
        surgicalHistory = strip(surgicalHistory);
        familyHistory = strip(familyHistory);
    }

    static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "ClinicalHistoryData[...]";
    }

}
