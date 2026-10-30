package ec.rehabelite.patients.application;

import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientProfile;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link PatientDirectory} for the other modules, backed by the patient repository. */
@Service
@RequiredArgsConstructor
class PatientDirectoryService implements PatientDirectory {

    private final PatientRepository patientRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientCard> find(Long patientId) {
        return patientRepository.findById(patientId).map(PatientDirectoryService::toCard);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientProfile> findProfile(Long patientId) {
        return patientRepository.findById(patientId).map(PatientDirectoryService::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientCard> search(String query, Pageable pageable) {
        return patientRepository.search(query, pageable).map(PatientDirectoryService::toCard);
    }

    private static PatientCard toCard(Patient patient) {
        return new PatientCard(patient.getId(), patient.getNationalId(), patient.getMedicalRecordNumber(),
                patient.getFirstName(), patient.getLastName(), patient.getBirthDate());
    }

    private static PatientProfile toProfile(Patient patient) {
        return new PatientProfile(patient.getId(), patient.getNationalId(), patient.getMedicalRecordNumber(),
                patient.getFirstName(), patient.getLastName(), patient.getBirthDate(), patient.getSex().name(),
                patient.getPhone(), patient.getEmail(), patient.getAddress(), patient.getOccupation(),
                patient.getEducationLevel(), patient.getHealthInsurance(), patient.getAdmissionDate());
    }

}
