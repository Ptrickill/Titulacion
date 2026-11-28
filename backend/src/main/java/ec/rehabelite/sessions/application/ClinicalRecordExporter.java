package ec.rehabelite.sessions.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.ClinicalRecordDocument;
import ec.rehabelite.clinicalrecords.ClinicalRecordDocument.TreatmentDocument;
import ec.rehabelite.clinicalrecords.ClinicalRecordExportPort;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientProfile;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.domain.TherapySessionRepository;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export of the whole clinical record of a patient (MEJ-06), which the physiotherapist hands to the data subject
 * (right of access and portability): identification, habits and medical history, and every treatment with its plan,
 * responsibles, status changes and sessions, including the internal clinical observations and the progress. It lives
 * in the sessions module because only this module may read those notes ({@code SessionsPublicApiPrivacyTests}).
 * Audited as EXPORT ClinicalRecord.
 */
@Service
@RequiredArgsConstructor
public class ClinicalRecordExporter {

    static final String AUDITED_ENTITY = "ClinicalRecord";
    static final String PATIENT_NOT_FOUND = "El paciente no existe.";
    static final String RECORD_NOT_FOUND = "El paciente no tiene historial clínico.";

    private static final Logger log = LoggerFactory.getLogger(ClinicalRecordExporter.class);

    private final PatientDirectory patients;
    private final ClinicalRecordExportPort records;
    private final TherapySessionRepository sessionRepository;
    private final StaffDirectory staffDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** The export: who generated it and when, the patient's identification and the record with its sessions. */
    public record ClinicalRecordExport(Instant generatedAt, String generatedBy, PatientIdentification patient,
            RecordExport clinicalRecord) {

        /** Never print clinical data, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "ClinicalRecordExport[...]";
        }

    }

    /** Only what identifies the record: no contact data (minimization). */
    public record PatientIdentification(Long id, String nationalId, String medicalRecordNumber, String firstName,
            String lastName, LocalDate birthDate, String sex) {
    }

    public record RecordExport(Long id, String habits, String physicalActivity, String allergies,
            String currentMedication, String personalHistory, String surgicalHistory, String familyHistory,
            Instant createdAt, Instant updatedAt, List<TreatmentExport> treatments) {
    }

    public record TreatmentExport(Long id, String diagnosisCode, String diagnosisDescription, String chiefComplaint,
            String currentIllness, String referringPhysician, String complementaryTests, String severity,
            String painType, String painLocation, int initialPainScore, String painFrequency, String sessionFrequency,
            String goal, int plannedSessions, String status, LocalDate startDate, List<ResponsibleExport> responsibles,
            List<StatusChangeExport> statusChanges, List<SessionExport> sessions) {
    }

    public record ResponsibleExport(Long physiotherapistId, String name, Instant validFrom, Instant validTo) {
    }

    public record StatusChangeExport(String previousStatus, String newStatus, String reason, String changedByName,
            Instant changedAt) {
    }

    public record SessionExport(int sequenceNumber, LocalDate sessionDate, LocalTime sessionTime, String sessionType,
            String activitiesPerformed, String clinicalObservations, String patientInstructions, int painScore,
            String progressStatus, String status, String responsibleName) {
    }

    /**
     * The export of the patient's clinical record, audited as EXPORT ClinicalRecord with the record's id. Errors are
     * not audited.
     *
     * @throws ResourceNotFoundException if the patient does not exist or has no clinical record
     */
    @Transactional
    public ClinicalRecordExport export(Long patientId) {
        PatientProfile profile = patients.findProfile(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
        ClinicalRecordDocument record = records.documentOf(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(RECORD_NOT_FOUND));

        List<Long> treatmentIds = record.treatments().stream().map(TreatmentDocument::id).toList();
        Map<Long, List<TherapySession>> sessions = treatmentIds.isEmpty() ? Map.of()
                : sessionRepository.findByTreatmentIdInOrderBySessionDateAscSessionTimeAscSequenceNumberAsc(
                        treatmentIds).stream().collect(Collectors.groupingBy(TherapySession::getTreatmentId));
        Map<Long, String> names = staffDirectory.fullNames(staffIds(record, sessions));

        auditLogService.record(AuditAction.EXPORT, AUDITED_ENTITY, record.recordId());
        log.info("Clinical record exported recordId={} patientId={}", record.recordId(), patientId);
        return new ClinicalRecordExport(clock.instant(),
                currentUserProvider.currentUser().map(CurrentUser::fullName).orElse(null),
                new PatientIdentification(profile.id(), profile.nationalId(), profile.medicalRecordNumber(),
                        profile.firstName(), profile.lastName(), profile.birthDate(), profile.sex()),
                new RecordExport(record.recordId(), record.habits(), record.physicalActivity(), record.allergies(),
                        record.currentMedication(), record.personalHistory(), record.surgicalHistory(),
                        record.familyHistory(), record.createdAt(), record.updatedAt(),
                        record.treatments().stream()
                                .map(t -> treatment(t, sessions.getOrDefault(t.id(), List.of()), names))
                                .toList()));
    }

    /**
     * The same export as a PDF, built inside the transaction: if it fails, no audit entry is left.
     *
     * @throws ResourceNotFoundException if the patient does not exist or has no clinical record
     */
    @Transactional
    public byte[] exportPdf(Long patientId) {
        return ClinicalRecordPdf.render(export(patientId));
    }

    private static Set<Long> staffIds(ClinicalRecordDocument record, Map<Long, List<TherapySession>> sessions) {
        Set<Long> ids = new HashSet<>();
        for (TreatmentDocument treatment : record.treatments()) {
            treatment.responsibles().forEach(r -> ids.add(r.physiotherapistId()));
            treatment.statusChanges().forEach(c -> ids.add(c.changedById()));
        }
        sessions.values().forEach(list -> list.forEach(s -> ids.add(s.getResponsibleId())));
        return ids;
    }

    private static TreatmentExport treatment(TreatmentDocument t, List<TherapySession> sessions,
            Map<Long, String> names) {
        return new TreatmentExport(t.id(), t.diagnosisCode(), t.diagnosisDescription(), t.chiefComplaint(),
                t.currentIllness(), t.referringPhysician(), t.complementaryTests(), t.severity(), t.painType(),
                t.painLocation(), t.initialPainScore(), t.painFrequency(), t.sessionFrequency(), t.goal(),
                t.plannedSessions(), t.status(), t.startDate(),
                t.responsibles().stream().map(r -> new ResponsibleExport(r.physiotherapistId(),
                        names.get(r.physiotherapistId()), r.validFrom(), r.validTo())).toList(),
                t.statusChanges().stream().map(c -> new StatusChangeExport(c.previousStatus(), c.newStatus(),
                        c.reason(), names.get(c.changedById()), c.changedAt())).toList(),
                sessions.stream().map(s -> new SessionExport(s.getSequenceNumber(), s.getSessionDate(),
                        s.getSessionTime(), s.getSessionType(), s.getActivitiesPerformed(), s.getClinicalObservations(),
                        s.getPatientInstructions(), s.getPainScore(), s.getProgressStatus().name(),
                        s.getStatus().name(), names.get(s.getResponsibleId()))).toList());
    }

}
