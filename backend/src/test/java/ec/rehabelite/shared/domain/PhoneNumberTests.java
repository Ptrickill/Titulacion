package ec.rehabelite.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTests {

    @ParameterizedTest
    @ValueSource(strings = {"0991234567", "0987654321", "022345678", "032345678", "042345678", "052345678",
        "062345678", "072345678"})
    void acceptsAMobileOrALandlineWithItsProvinceCode(String phone) {
        assertThat(PhoneNumber.isValid(phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "099123456", "09912345678", "0891234567", "012345678", "082345678", "092345678",
        "22345678", "+593991234567", "0991 234 567", "099-123-4567", "09912345a7"})
    void rejectsEverythingElse(String phone) {
        assertThat(PhoneNumber.isValid(phone)).isFalse();
    }

    @Test
    void nullIsNotAPhone() {
        assertThat(PhoneNumber.isValid(null)).isFalse();
    }

}
