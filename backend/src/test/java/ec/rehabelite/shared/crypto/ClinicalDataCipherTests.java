package ec.rehabelite.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.config.AppProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** MEJ-06: AES-256-GCM of the clinical text, with a random IV per value, strict reading and key rotation. */
class ClinicalDataCipherTests {

    private static final String KEY = "AMRp+cyxpXVxQnCIXks0M2MdvAYMHYNvP5Rl4YUbhYM=";
    private static final String OTHER_KEY = "j03RbNtXHZC4nOqoDapOid/u2BmBV1SyT3gxbAe9Z0o=";
    private static final String TEXT = "Alergia a la penicilina; dolor lumbar desde hace tres semanas";

    private final ClinicalDataCipher cipher = ClinicalDataCipher.of(new AppProperties.ClinicalData(KEY, ""));

    @Test
    void encryptsWithTheVersionedPrefixAndDecryptsTheSameText() {
        String stored = cipher.encrypt(TEXT);

        assertThat(stored).startsWith("enc:v1:").doesNotContain(TEXT).doesNotContain("penicilina");
        assertThat(stored.split(":")).hasSize(4);
        assertThat(cipher.decrypt(stored)).isEqualTo(TEXT);
        assertThat(cipher.usesCurrentKey(stored)).isTrue();
    }

    @Test
    void everyEncryptionUsesANewIvSoTheSameTextNeverRepeatsItsValue() {
        assertThat(cipher.encrypt(TEXT)).isNotEqualTo(cipher.encrypt(TEXT));
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
        assertThat(cipher.decrypt(cipher.encrypt("Ñandú, señal y acción"))).isEqualTo("Ñandú, señal y acción");
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void plainTextIsRejectedWithoutShowingIt() {
        assertThatThrownBy(() -> cipher.decrypt(TEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ClinicalDataCipher.NOT_ENCRYPTED)
                .hasMessageNotContaining("penicilina");
    }

    @Test
    void anAlteredValueIsRejected() {
        String stored = cipher.encrypt(TEXT);
        String prefix = stored.substring(0, stored.lastIndexOf(':') + 1);
        byte[] payload = Base64.getDecoder().decode(stored.substring(prefix.length()));
        payload[payload.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(prefix + Base64.getEncoder().encodeToString(payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ClinicalDataCipher.CORRUPTED);
        assertThatThrownBy(() -> cipher.decrypt(prefix + "no-es-base64!"))
                .hasMessage(ClinicalDataCipher.CORRUPTED);
    }

    @Test
    void aValueOfAnotherKeyNeedsThatKeyAmongThePreviousOnes() {
        ClinicalDataCipher other = ClinicalDataCipher.of(new AppProperties.ClinicalData(OTHER_KEY, ""));
        String byOther = other.encrypt(TEXT);

        assertThatThrownBy(() -> cipher.decrypt(byOther)).hasMessage(ClinicalDataCipher.UNKNOWN_KEY);

        // After a rotation: the new key encrypts, the previous one still decrypts what it encrypted
        ClinicalDataCipher rotated = ClinicalDataCipher.of(new AppProperties.ClinicalData(KEY, " " + OTHER_KEY + " "));
        assertThat(rotated.decrypt(byOther)).isEqualTo(TEXT);
        assertThat(rotated.usesCurrentKey(byOther)).isFalse();
        assertThat(rotated.usesCurrentKey(rotated.encrypt(TEXT))).isTrue();
    }

}
