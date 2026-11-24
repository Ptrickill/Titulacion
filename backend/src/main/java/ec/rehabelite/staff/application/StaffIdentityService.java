package ec.rehabelite.staff.application;

import ec.rehabelite.patients.RegisteredIdentities;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The staff side of {@link RegisteredIdentities} (MEJ-02): the patients module asks here whether a national ID or an
 * email belongs to a staff member, without depending on the staff module.
 */
@Service
@RequiredArgsConstructor
class StaffIdentityService implements RegisteredIdentities {

    private final StaffRecordRepository staffRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> staffWithNationalId(String nationalId) {
        return staffRepository.findByNationalId(nationalId).map(StaffRecord::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> staffWithEmail(String email) {
        return staffRepository.findByEmail(email).map(StaffRecord::getId);
    }

}
