package ec.rehabelite.patients.application;

import ec.rehabelite.patients.domain.PersonalData;
import java.time.LocalDate;

/**
 * Admission and personal data of a new patient; the medical record number is assigned by the server. MEJ-06: the
 * versions of the consent texts the patient signed, DATA_PROCESSING always and EMAIL_COMMUNICATIONS when given (null
 * otherwise).
 */
public record RegisterPatientCommand(String nationalId, LocalDate admissionDate, PersonalData personalData,
        String dataProcessingConsentVersion, String emailCommunicationsConsentVersion) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "RegisterPatientCommand[...]";
    }

}
