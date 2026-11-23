package ec.rehabelite.patients.application;

import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientProfile;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientName;
import ec.rehabelite.patients.domain.PatientRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final AddressText addressText;

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientCard> find(Long patientId) {
        return patientRepository.findById(patientId).map(PatientDirectoryService::toCard);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientCard> findByPortalUser(Long userId) {
        return patientRepository.findByPortalUserId(userId).map(PatientDirectoryService::toCard);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientProfile> findProfile(Long patientId) {
        return patientRepository.findById(patientId).map(this::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientCard> search(String query, Pageable pageable) {
        return patientRepository.search(query, pageable).map(PatientDirectoryService::toCard);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> fullNames(Collection<Long> patientIds) {
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return patientRepository.findNamesByIdIn(patientIds).stream()
                .collect(Collectors.toMap(PatientName::getId,
                        name -> name.getFirstName() + " " + name.getLastName()));
    }

    private static PatientCard toCard(Patient patient) {
        return new PatientCard(patient.getId(), patient.getNationalId(), patient.getMedicalRecordNumber(),
                patient.getFirstName(), patient.getLastName(), patient.getBirthDate());
    }

    private PatientProfile toProfile(Patient patient) {
        AddressView address = addressText.view(patient.getAddress());
        return new PatientProfile(patient.getId(), patient.getNationalId(), patient.getMedicalRecordNumber(),
                patient.getFirstName(), patient.getLastName(), patient.getBirthDate(), patient.getSex().name(),
                patient.getPhone(), patient.getEmail(), address.formatted(), address.complete(),
                patient.getOccupation(), patient.getEducationLevel(), patient.isHasHealthInsurance(),
                patient.getAdmissionDate());
    }

}
