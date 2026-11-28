package ec.rehabelite.sessions.application;

import ec.rehabelite.sessions.application.ClinicalRecordExporter.ClinicalRecordExport;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.PatientIdentification;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.RecordExport;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.ResponsibleExport;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.SessionExport;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.StatusChangeExport;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.TreatmentExport;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.pdf.PdfLayout;
import ec.rehabelite.shared.pdf.PdfLayout.Column;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The PDF of the clinical record export (MEJ-06), with the same content as its JSON: identification, habits and
 * medical history, and every treatment with its plan, responsibles, status changes and sessions. Spanish labels, as
 * the frontend shows them.
 */
final class ClinicalRecordPdf {

    static final String TITLE = "Historia clínica";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<Column> FIELDS = List.of(new Column("Dato", 1, false), new Column("Valor", 3, false));
    private static final Map<String, String> SEX = Map.of("MALE", "Masculino", "FEMALE", "Femenino");
    private static final Map<String, String> SEVERITY = Map.of("MILD", "Leve", "MODERATE", "Moderada",
            "SEVERE", "Grave");
    private static final Map<String, String> TREATMENT_STATUS = Map.of("ACTIVE", "Activo", "COMPLETED", "Finalizado",
            "ABANDONED", "Abandonado");
    private static final Map<String, String> SESSION_STATUS = Map.of("COMPLETED", "Realizada", "RESCHEDULED",
            "Reprogramada", "NO_SHOW", "No asistió");
    private static final Map<String, String> PROGRESS = Map.of("STABLE", "Estable", "IMPROVING", "En mejoría",
            "NEEDS_ATTENTION", "Requiere atención");

    private ClinicalRecordPdf() {
    }

    static byte[] render(ClinicalRecordExport export) {
        PdfLayout pdf = new PdfLayout(TITLE);
        PatientIdentification patient = export.patient();
        pdf.text(TITLE, PdfLayout.TITLE);
        pdf.text(patient.firstName() + " " + patient.lastName(), PdfLayout.HEADING);
        pdf.text("Generado el " + dateTime(export.generatedAt()) + (export.generatedBy() == null ? ""
                : " por " + export.generatedBy()) + ". Documento confidencial: contiene datos de salud.",
                PdfLayout.NOTE);
        pdf.space(8);

        pdf.heading("Identificación del paciente");
        pdf.table(FIELDS, List.of(
                List.of("Cédula", patient.nationalId()),
                List.of("N.º de historia clínica", patient.medicalRecordNumber()),
                List.of("Fecha de nacimiento", patient.birthDate().format(DATE)),
                List.of("Sexo", SEX.getOrDefault(patient.sex(), patient.sex()))), false);

        RecordExport record = export.clinicalRecord();
        pdf.heading("Hábitos y antecedentes");
        pdf.table(FIELDS, List.of(
                List.of("Hábitos", record.habits()),
                List.of("Actividad física", record.physicalActivity()),
                List.of("Alergias", record.allergies()),
                List.of("Medicación actual", record.currentMedication()),
                List.of("Antecedentes personales", record.personalHistory()),
                List.of("Antecedentes quirúrgicos", record.surgicalHistory()),
                List.of("Antecedentes familiares", record.familyHistory())), false);

        int number = 1;
        for (TreatmentExport treatment : record.treatments()) {
            treatment(pdf, number++, treatment);
        }
        if (record.treatments().isEmpty()) {
            pdf.text("El historial no tiene tratamientos.", PdfLayout.BODY);
        }
        return pdf.finish("Rehab Elite · Historia clínica · N.º " + patient.medicalRecordNumber());
    }

    private static void treatment(PdfLayout pdf, int number, TreatmentExport t) {
        pdf.heading("Tratamiento " + number + " · " + TREATMENT_STATUS.getOrDefault(t.status(), t.status())
                + " · desde el " + t.startDate().format(DATE));
        pdf.table(FIELDS, List.of(
                List.of("Diagnóstico CIE-10", t.diagnosisCode() + " · " + t.diagnosisDescription()),
                List.of("Motivo de consulta", t.chiefComplaint()),
                List.of("Enfermedad actual", t.currentIllness()),
                List.of("Médico remitente", t.referringPhysician()),
                List.of("Exámenes complementarios", t.complementaryTests()),
                List.of("Gravedad", SEVERITY.getOrDefault(t.severity(), t.severity())),
                List.of("Dolor", t.painType() + " · " + t.painLocation() + " · " + t.painFrequency()),
                List.of("EVA inicial", t.initialPainScore() + "/10"),
                List.of("Objetivo", t.goal()),
                List.of("Plan", t.plannedSessions() + " sesiones · " + t.sessionFrequency())), false);

        if (!t.responsibles().isEmpty()) {
            pdf.text("Responsables", PdfLayout.BODY);
            List<List<String>> rows = new ArrayList<>();
            for (ResponsibleExport r : t.responsibles()) {
                rows.add(List.of(name(r.name()), dateTime(r.validFrom()),
                        r.validTo() == null ? "Vigente" : dateTime(r.validTo())));
            }
            pdf.table(List.of(new Column("Fisioterapeuta", 2, false), new Column("Desde", 1, false),
                    new Column("Hasta", 1, false)), rows, true);
        }
        if (!t.statusChanges().isEmpty()) {
            pdf.text("Cambios de estado", PdfLayout.BODY);
            List<List<String>> rows = new ArrayList<>();
            for (StatusChangeExport c : t.statusChanges()) {
                rows.add(List.of(dateTime(c.changedAt()), TREATMENT_STATUS.getOrDefault(c.previousStatus(),
                        c.previousStatus()) + " → " + TREATMENT_STATUS.getOrDefault(c.newStatus(), c.newStatus()),
                        c.reason(), name(c.changedByName())));
            }
            pdf.table(List.of(new Column("Fecha", 1, false), new Column("Cambio", 1.2f, false),
                    new Column("Motivo", 2.5f, false), new Column("Por", 1.3f, false)), rows, true);
        }
        pdf.text("Sesiones (" + t.sessions().size() + ")", PdfLayout.BODY);
        if (t.sessions().isEmpty()) {
            pdf.text("Sin sesiones registradas.", PdfLayout.NOTE);
        }
        for (SessionExport s : t.sessions()) {
            pdf.table(FIELDS, List.of(
                    List.of("Sesión " + String.format("%02d", s.sequenceNumber()),
                            s.sessionDate().format(DATE) + " " + s.sessionTime().format(TIME) + " · "
                                    + s.sessionType()),
                    List.of("Estado", SESSION_STATUS.getOrDefault(s.status(), s.status()) + " · EVA " + s.painScore()
                            + "/10 · " + PROGRESS.getOrDefault(s.progressStatus(), s.progressStatus())),
                    List.of("Fisioterapeuta", name(s.responsibleName())),
                    List.of("Actividades", s.activitiesPerformed()),
                    List.of("Observaciones clínicas", s.clinicalObservations()),
                    List.of("Indicaciones", s.patientInstructions() == null ? "—" : s.patientInstructions())), false);
            pdf.space(4);
        }
    }

    private static String name(String name) {
        return name == null ? "—" : name;
    }

    private static String dateTime(Instant instant) {
        return instant.atZone(ClinicCalendar.ZONE).format(DATE_TIME);
    }

}
