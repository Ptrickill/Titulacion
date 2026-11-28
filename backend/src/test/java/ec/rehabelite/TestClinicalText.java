package ec.rehabelite;

import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.crypto.ClinicalDataCipher;
import java.util.HashMap;
import java.util.Map;

/**
 * Clinical text for the tests that insert or read clinical rows with SQL (MEJ-06): the converter is strict, so the
 * columns must hold encrypted values. Same test-only key as application-test.yml.
 */
public final class TestClinicalText {

    /** app.clinical-data.key of application-test.yml. */
    public static final String KEY = "AMRp+cyxpXVxQnCIXks0M2MdvAYMHYNvP5Rl4YUbhYM=";

    private static final ClinicalDataCipher CIPHER = ClinicalDataCipher.of(new AppProperties.ClinicalData(KEY, ""));

    private TestClinicalText() {
    }

    public static String encrypt(String text) {
        return CIPHER.encrypt(text);
    }

    public static String decrypt(String storedValue) {
        return CIPHER.decrypt(storedValue);
    }

    /** The row with the given columns decrypted; the other columns as they are. */
    public static Map<String, Object> decryptColumns(Map<String, Object> row, String... columns) {
        Map<String, Object> decrypted = new HashMap<>(row);
        for (String column : columns) {
            decrypted.put(column, decrypt((String) row.get(column)));
        }
        return decrypted;
    }

}
