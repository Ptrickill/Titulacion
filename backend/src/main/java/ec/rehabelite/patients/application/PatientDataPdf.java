package ec.rehabelite.patients.application;

import ec.rehabelite.patients.ConsentChannel;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.application.PatientDataExportService.ConsentExport;
import ec.rehabelite.patients.application.PatientDataExportService.PatientDataExport;
import ec.rehabelite.patients.application.PatientDataExportService.PersonalDataExport;
import ec.rehabelite.patients.application.PatientDataExportService.RequestExport;
import ec.rehabelite.patients.domain.DataRequestStatus;
import ec.rehabelite.patients.domain.DataRequestType;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.pdf.PdfLayout;
import ec.rehabelite.shared.pdf.PdfLayout.Column;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The PDF of the export of a patient's personal data (MEJ-06), with the same content as its JSON, and the note that
 * the clinical record is exported by the physiotherapist.
 */
final class PatientDataPdf {

    static final String TITLE = "Datos personales del paciente";
    static final String CLINICAL_RECORD_NOTE = "Este documento no incluye la historia clínica: la entrega el "
            + "fisioterapeuta desde el expediente del paciente.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<Column> FIELDS = List.of(new Column("Dato", 1, false), new Column("Valor", 2.5f, false));
    private static final Map<String, String> SEX = Map.of("MALE", "Masculino", "FEMALE", "Femenino");
    private static final Map<String, String> ACCOUNT_STATUS = Map.of("PENDING_ACTIVATION", "Pendiente de activación",
            "ACTIVE", "Activa", "INACTIVE", "Desactivada");
    private static final Map<ConsentType, String> CONSENT_TYPE = Map.of(
            ConsentType.DATA_PROCESSING, "Tratamiento de datos personales y de salud",
            ConsentType.EMAIL_COMMUNICATIONS, "Comunicaciones por correo");
    private static final Map<ConsentChannel, String> CHANNEL = Map.of(
            ConsentChannel.PHYSICAL_SIGNATURE, "Firma física", ConsentChannel.PORTAL, "Portal del paciente",
            ConsentChannel.ERASURE, "Solicitud de eliminación");
    private static final Map<DataRequestType, String> REQUEST_TYPE = Map.of(
            DataRequestType.ACCESS, "Acceso", DataRequestType.RECTIFICATION, "Rectificación",
            DataRequestType.UPDATE, "Actualización", DataRequestType.OBJECTION, "Oposición",
            DataRequestType.ERASURE, "Eliminación", DataRequestType.PORTABILITY, "Portabilidad");
    private static final Map<DataRequestStatus, String> REQUEST_STATUS = Map.of(
            DataRequestStatus.RECEIVED, "Recibida", DataRequestStatus.IN_PROGRESS, "En curso",
            DataRequestStatus.RESOLVED, "Resuelta", DataRequestStatus.REJECTED, "Rechazada");

    private PatientDataPdf() {
    }

    static byte[] render(PatientDataExport export) {
        PdfLayout pdf = new PdfLayout(TITLE);
        PersonalDataExport p = export.patient();
        pdf.text(TITLE, PdfLayout.TITLE);
        pdf.text(p.firstName() + " " + p.lastName(), PdfLayout.HEADING);
        pdf.text("Generado el " + dateTime(export.generatedAt()) + (export.generatedBy() == null ? ""
                : " por " + export.generatedBy()) + ". " + CLINICAL_RECORD_NOTE, PdfLayout.NOTE);
        pdf.space(8);

        pdf.heading("Datos personales");
        List<List<String>> personal = new ArrayList<>(List.of(
                List.of("Cédula", p.nationalId()),
                List.of("N.º de historia clínica", p.medicalRecordNumber()),
                List.of("Nombres", p.firstName()),
                List.of("Apellidos", p.lastName()),
                List.of("Fecha de nacimiento", p.birthDate().format(DATE)),
                List.of("Sexo", SEX.getOrDefault(p.sex(), p.sex())),
                List.of("Teléfono", blank(p.phone())),
                List.of("Correo", blank(p.email())),
                List.of("Dirección", blank(p.address())),
                List.of("Ocupación", p.occupation()),
                List.of("Nivel de instrucción", p.educationLevel()),
                List.of("Seguro de salud", p.hasHealthInsurance() ? "Sí" : "No"),
                List.of("Autoidentificación étnica", blank(p.ethnicSelfIdentification())),
                List.of("Religión", blank(p.religion())),
                List.of("Fecha de ingreso", p.admissionDate().format(DATE)),
                List.of("Registrado el", dateTime(p.registeredAt()))));
        if (p.blockedAt() != null) {
            personal.add(List.of("Bloqueado el", dateTime(p.blockedAt()) + " (solicitud de eliminación aceptada)"));
        }
        pdf.table(FIELDS, personal, false);

        pdf.heading("Acceso al portal");
        pdf.text(export.portalAccount() == null ? "Sin cuenta de acceso al portal."
                : "Cuenta " + ACCOUNT_STATUS.getOrDefault(export.portalAccount().status(),
                        export.portalAccount().status()).toLowerCase()
                        + (export.portalAccount().lastLoginAt() == null ? ", sin accesos."
                                : ", último acceso el " + dateTime(export.portalAccount().lastLoginAt()) + "."),
                PdfLayout.BODY);

        pdf.heading("Consentimientos");
        if (export.consents().isEmpty()) {
            pdf.text("Sin consentimientos registrados.", PdfLayout.BODY);
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (ConsentExport c : export.consents()) {
                rows.add(List.of(CONSENT_TYPE.get(c.type()), c.textVersion(), CHANNEL.get(c.channel()) + ", "
                        + dateTime(c.grantedAt()), closing(c)));
            }
            pdf.table(List.of(new Column("Consentimiento", 2, false), new Column("Versión", 1, false),
                    new Column("Otorgado", 1.6f, false), new Column("Estado", 1.6f, false)), rows, true);
        }

        pdf.heading("Solicitudes del titular");
        if (export.requests().isEmpty()) {
            pdf.text("Sin solicitudes registradas.", PdfLayout.BODY);
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (RequestExport r : export.requests()) {
                rows.add(List.of(REQUEST_TYPE.get(r.type()), r.receivedOn().format(DATE),
                        REQUEST_STATUS.get(r.status()) + (r.resolvedAt() == null ? "" : ", " + dateTime(r.resolvedAt())),
                        blank(r.response())));
            }
            pdf.table(List.of(new Column("Tipo", 1, false), new Column("Recibida", 1, false),
                    new Column("Estado", 1.4f, false), new Column("Respuesta", 2.6f, false)), rows, true);
        }
        return pdf.finish("Rehab Elite · Datos personales · N.º " + p.medicalRecordNumber());
    }

    private static String closing(ConsentExport c) {
        if (c.revokedAt() != null) {
            return "Revocado el " + dateTime(c.revokedAt()) + " (" + CHANNEL.get(c.revocationChannel()) + ")";
        }
        if (c.replacedAt() != null) {
            return "Reemplazado por una versión nueva el " + dateTime(c.replacedAt());
        }
        return "Vigente";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String dateTime(Instant instant) {
        return instant.atZone(ClinicCalendar.ZONE).format(DATE_TIME);
    }

}
