package ec.rehabelite.shared.crypto;

import ec.rehabelite.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encryption of the clinical free text (MEJ-06): AES-256-GCM with a random 12-byte IV per value and a 128-bit tag.
 * A value is stored as {@code enc:v1:<keyId>:<Base64(IV || ciphertext || tag)>}, where the key id is the first 8 hex
 * digits of the SHA-256 of the key: the current key (CLINICAL_DATA_KEY) encrypts, and any configured key, current or
 * previous (CLINICAL_DATA_PREVIOUS_KEYS), decrypts what it encrypted, so a rotation needs no downtime.
 * <p>
 * Strict on purpose: decrypting anything that is not an encrypted value is an error, so plain text never goes
 * unnoticed. Neither the values nor the keys ever reach a log or an exception message.
 */
@Component
public class ClinicalDataCipher {

    public static final String PREFIX = "enc:v1:";
    static final String NOT_ENCRYPTED = "The column holds a value that is not encrypted clinical text";
    static final String UNKNOWN_KEY = "The clinical text was encrypted with a key that is not configured";
    static final String CORRUPTED = "The encrypted clinical text cannot be decrypted";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_ID_HEX_DIGITS = 8;

    private final SecureRandom random = new SecureRandom();
    private final String currentKeyId;
    private final SecretKey currentKey;
    private final Map<String, SecretKey> keys = new LinkedHashMap<>();

    @Autowired
    public ClinicalDataCipher(AppProperties properties) {
        this(properties.clinicalData());
    }

    private ClinicalDataCipher(AppProperties.ClinicalData config) {
        currentKey = toKey(config.key());
        currentKeyId = keyIdOf(currentKey);
        keys.put(currentKeyId, currentKey);
        for (String previous : config.previousKeyList()) {
            SecretKey key = toKey(previous);
            keys.putIfAbsent(keyIdOf(key), key);
        }
    }

    /** A cipher with the given keys, outside Spring (e.g. the tests that insert clinical text with SQL). */
    public static ClinicalDataCipher of(AppProperties.ClinicalData config) {
        return new ClinicalDataCipher(config);
    }

    /** Whether the stored value is already encrypted (with any key). */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** The encrypted form of the text, with a new random IV each time; null stays null. */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + currentKeyId + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clinical text encryption failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * The text of an encrypted value; null stays null.
     *
     * @throws IllegalStateException if the value is not encrypted, its key is not configured or it was altered
     */
    public String decrypt(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (!isEncrypted(storedValue)) {
            throw new IllegalStateException(NOT_ENCRYPTED);
        }
        int separator = storedValue.indexOf(':', PREFIX.length());
        if (separator < 0) {
            throw new IllegalStateException(CORRUPTED);
        }
        SecretKey key = keys.get(storedValue.substring(PREFIX.length(), separator));
        if (key == null) {
            throw new IllegalStateException(UNKNOWN_KEY);
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(separator + 1));
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException(CORRUPTED);
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(CORRUPTED);
        }
    }

    /** Whether the value was encrypted with the current key (false for null or plain text). */
    public boolean usesCurrentKey(String storedValue) {
        return isEncrypted(storedValue) && storedValue.startsWith(PREFIX + currentKeyId + ":");
    }

    private static SecretKey toKey(String base64) {
        return new SecretKeySpec(Base64.getDecoder().decode(base64.strip()), "AES");
    }

    private static String keyIdOf(SecretKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return HexFormat.of().formatHex(digest).substring(0, KEY_ID_HEX_DIGITS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

}
