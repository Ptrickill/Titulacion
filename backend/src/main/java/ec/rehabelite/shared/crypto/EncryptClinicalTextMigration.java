package ec.rehabelite.shared.crypto;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.stereotype.Component;

/**
 * V11 (MEJ-06): encrypts the clinical text that existing databases hold in plain text, the columns of
 * {@link ClinicalTextColumns} including the Envers {@code _aud} tables, with CLINICAL_DATA_KEY. A Java migration
 * because it needs the key; it is a Spring bean, and Spring Boot hands every {@code JavaMigration} bean to Flyway. It
 * implements {@link JavaMigration} directly, so its version does not come from the class name. Values that are
 * already encrypted are skipped, so it is safe on a database that already has encrypted rows.
 */
@Component
public class EncryptClinicalTextMigration implements JavaMigration {

    private final ClinicalTextEncryptor encryptor;

    public EncryptClinicalTextMigration(ClinicalTextEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("11");
    }

    @Override
    public String getDescription() {
        return "encrypt clinical text";
    }

    /** No checksum: the class is not a script, and its content may evolve without invalidating the history. */
    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws Exception {
        encryptor.encryptPlainText(context.getConnection());
    }

}
