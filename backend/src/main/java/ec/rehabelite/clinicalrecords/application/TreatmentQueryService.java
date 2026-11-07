package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link TreatmentQueryPort} for the other modules. */
@Service
@RequiredArgsConstructor
class TreatmentQueryService implements TreatmentQueryPort {

    private final TreatmentRepository treatmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TreatmentSummary> find(Long treatmentId) {
        return treatmentRepository.findById(treatmentId).map(TreatmentQueryService::summaryOf);
    }

    /** MANDATORY: the lock is only useful while the caller's transaction is still open. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TreatmentSummary> lockForSessionRegistration(Long treatmentId) {
        return treatmentRepository.findForUpdate(treatmentId).map(TreatmentQueryService::summaryOf);
    }

    private static TreatmentSummary summaryOf(Treatment treatment) {
        return new TreatmentSummary(treatment.getId(), treatment.getClinicalRecord().getPatientId(),
                treatment.isActive(), treatment.getStartDate());
    }

}
