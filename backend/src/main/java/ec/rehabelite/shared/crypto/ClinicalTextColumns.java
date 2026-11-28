package ec.rehabelite.shared.crypto;

import java.util.List;

/**
 * Every column encrypted with {@link EncryptedText} (MEJ-06), including the Envers {@code _aud} tables. V11 and the
 * dev seed encrypt the rows that still hold plain text from this list; an entity field that uses the converter must
 * be listed here, and {@code ClinicalDataEncryptionIntegrationTests} checks that the lists agree.
 */
public final class ClinicalTextColumns {

    /** A table and its encrypted text columns. */
    public record Table(String name, List<String> columns) {
    }

    public static final List<Table> ALL = List.of(
            new Table("clinical_records", List.of("habits", "physical_activity", "allergies", "current_medication",
                    "personal_history", "surgical_history", "family_history")),
            new Table("treatments", List.of("chief_complaint", "current_illness", "complementary_tests", "goal")),
            new Table("treatments_aud", List.of("chief_complaint", "current_illness", "complementary_tests",
                    "goal")),
            new Table("treatment_status_changes", List.of("reason")),
            new Table("therapy_sessions", List.of("activities_performed", "clinical_observations",
                    "patient_instructions")),
            new Table("therapy_sessions_aud", List.of("activities_performed", "clinical_observations",
                    "patient_instructions")),
            new Table("ai_summaries", List.of("generated_content", "final_content")));

    private ClinicalTextColumns() {
    }

}
