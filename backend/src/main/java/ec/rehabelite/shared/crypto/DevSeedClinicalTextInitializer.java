package ec.rehabelite.shared.crypto;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dev profile only (MEJ-06): the seed script (db/dev-seed/R__dev_seed.sql) inserts its synthetic clinical text in
 * plain text through SQL, after V11 already ran; this encrypts it at startup, before anything reads it, like
 * {@code DevSeedPasswordInitializer} does with the passwords. Encrypted values are skipped.
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
class DevSeedClinicalTextInitializer implements ApplicationRunner {

    private final ClinicalTextEncryptor encryptor;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    DevSeedClinicalTextInitializer(ClinicalTextEncryptor encryptor, JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.encryptor = encryptor;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.execute(
                (ConnectionCallback<Integer>) encryptor::encryptPlainText));
    }

}
