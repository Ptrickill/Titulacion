package ec.rehabelite.shared.crypto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Encrypts the clinical text that is still plain in the database (MEJ-06): every column of
 * {@link ClinicalTextColumns}, in batches of {@value #BATCH_SIZE} rows read by id. A value that is already encrypted is
 * left as it is, so running it again changes nothing. Used by the V11 migration on existing data and by the dev seed,
 * which inserts plain text through SQL. It logs only counts, never a value.
 */
@Component
public class ClinicalTextEncryptor {

    static final int BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(ClinicalTextEncryptor.class);

    private final ClinicalDataCipher cipher;

    public ClinicalTextEncryptor(ClinicalDataCipher cipher) {
        this.cipher = cipher;
    }

    /**
     * Encrypts the plain values of every table on the given connection, in the caller's transaction.
     *
     * @return how many rows were updated
     */
    public int encryptPlainText(Connection connection) throws SQLException {
        int total = 0;
        for (ClinicalTextColumns.Table table : ClinicalTextColumns.ALL) {
            int rows = encryptTable(connection, table);
            if (rows > 0) {
                log.info("Clinical text encrypted table={} rows={}", table.name(), rows);
            }
            total += rows;
        }
        return total;
    }

    private int encryptTable(Connection connection, ClinicalTextColumns.Table table) throws SQLException {
        // The _aud tables have a composite key (id, rev); ctid identifies the row in both kinds of table
        String columns = String.join(", ", table.columns());
        String anyPlain = String.join(" OR ", table.columns().stream()
                .map(column -> "(" + column + " IS NOT NULL AND " + column + " NOT LIKE '" + ClinicalDataCipher.PREFIX
                        + "%')")
                .toList());
        String select = "SELECT ctid::text, " + columns + " FROM " + table.name() + " WHERE " + anyPlain
                + " LIMIT " + BATCH_SIZE;
        String update = "UPDATE " + table.name() + " SET "
                + String.join(", ", table.columns().stream().map(column -> column + " = ?").toList())
                + " WHERE ctid = ?::tid";

        int updated = 0;
        while (true) {
            List<Object[]> batch = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(select);
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Object[] row = new Object[table.columns().size() + 1];
                    row[0] = rows.getString(1);
                    for (int i = 0; i < table.columns().size(); i++) {
                        String value = rows.getString(i + 2);
                        row[i + 1] = value == null || ClinicalDataCipher.isEncrypted(value)
                                ? value
                                : cipher.encrypt(value);
                    }
                    batch.add(row);
                }
            }
            if (batch.isEmpty()) {
                return updated;
            }
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                for (Object[] row : batch) {
                    for (int i = 0; i < table.columns().size(); i++) {
                        statement.setString(i + 1, (String) row[i + 1]);
                    }
                    statement.setString(table.columns().size() + 1, (String) row[0]);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            updated += batch.size();
        }
    }

}
