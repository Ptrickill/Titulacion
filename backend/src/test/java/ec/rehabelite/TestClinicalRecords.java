package ec.rehabelite;

/** Request bodies of the clinical record and its treatments (HU-07) for the integration tests; synthetic data. */
public final class TestClinicalRecords {

    public static final String TREATMENT = """
            {"diagnosisCode":"M54.5","chiefComplaint":"Dolor lumbar al levantar peso",
             "currentIllness":"Tres semanas de evolución","referringPhysician":"Dr. Carlos Andrade",
             "complementaryTests":"Radiografía lumbar","severity":"MODERATE","painType":"Punzante",
             "painLocation":"Región lumbar","initialPainScore":6,"painFrequency":"Intermitente",
             "sessionFrequency":"3 veces por semana","goal":"Reducir el dolor","plannedSessions":10}""";

    private TestClinicalRecords() {
    }

    /** POST /api/patients/{id}/clinical-record: habits and history with the first treatment. */
    public static String record(String habits) {
        return """
                {"habits":"%s","physicalActivity":"Camina 30 minutos","allergies":"Ninguna conocida",
                 "currentMedication":"Ibuprofeno 400 mg","personalHistory":"Hipertensión controlada",
                 "surgicalHistory":"Apendicectomía (2010)","familyHistory":"Madre con artrosis","treatment":%s}"""
                .formatted(habits, TREATMENT);
    }

}
