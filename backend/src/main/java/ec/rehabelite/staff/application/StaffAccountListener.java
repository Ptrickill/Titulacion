package ec.rehabelite.staff.application;

import ec.rehabelite.auth.StaffAccountCreated;
import ec.rehabelite.staff.domain.StaffRecord;
import ec.rehabelite.staff.domain.StaffRecordRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the staff record of an account created outside the people registry: the initial administrator from
 * {@code app.admin} (MEJ-02). Synchronous ({@code @EventListener}) and in the publisher's transaction, so the account
 * and its record are created together or not at all. The record has the account's names and email, and the rest is
 * to be completed.
 */
@Component
@RequiredArgsConstructor
class StaffAccountListener {

    private static final Logger log = LoggerFactory.getLogger(StaffAccountListener.class);

    private final StaffRecordRepository staffRepository;
    private final Clock clock;

    @EventListener
    @Transactional
    public void on(StaffAccountCreated event) {
        if (staffRepository.existsByUserId(event.userId())) {
            return;
        }
        StaffRecord record = staffRepository.save(StaffRecord.forExistingAccount(event.userId(), event.email(),
                event.fullName(), clock.instant()));
        log.info("Staff record created for an existing account staffId={} userId={}", record.getId(),
                event.userId());
    }

}
