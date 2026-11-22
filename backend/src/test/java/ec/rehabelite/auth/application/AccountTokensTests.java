package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountTokensTests {

    @Test
    void tokensAre32RandomBytesInUrlSafeBase64WithoutPadding() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = AccountTokens.generate();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(200);
    }

    @Test
    void onlyTheSha256IsStored() {
        assertThat(AccountTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void describesTheLinkLifetimeInSpanish() {
        assertThat(AccountMailer.describe(Duration.ofHours(48))).isEqualTo("48 horas");
        assertThat(AccountMailer.describe(Duration.ofHours(1))).isEqualTo("1 hora");
        assertThat(AccountMailer.describe(Duration.ofMinutes(30))).isEqualTo("30 minutos");
    }

}
