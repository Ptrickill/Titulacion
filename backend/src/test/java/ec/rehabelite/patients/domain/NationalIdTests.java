package ec.rehabelite.patients.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Synthetic national IDs; the invalid ones break exactly one rule (their check digit is otherwise right). */
class NationalIdTests {

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "0100000009, first province (01)",
            "1710034065, Pichincha (17)",
            "0923456784, Guayas (09)",
            "2405123452, last province (24)",
            "3001234560, registered abroad (30), check digit 0",
            "1700000050, check digit 0",
            "1750000018, third digit 5",
    })
    void acceptsValidNationalIds(String nationalId, String description) {
        assertThat(NationalId.isValid(nationalId)).isTrue();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "171003406, nine digits",
            "17100340651, eleven digits",
            "17100340A5, a letter",
            "'1710 34065', a space",
            "'-710034065', a sign",
            "0010000008, province 00",
            "2510000009, province 25",
            "2910000005, province 29",
            "3110000001, province 31",
            "1760000008, third digit 6",
            "1790000002, third digit 9",
            "1710034064, wrong check digit",
            "1710034066, wrong check digit",
            "0000000000, all zeros",
    })
    void rejectsInvalidNationalIds(String nationalId, String description) {
        assertThat(NationalId.isValid(nationalId)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "１７１００３４０６５"})
    void rejectsMissingOrNonAsciiValues(String nationalId) {
        assertThat(NationalId.isValid(nationalId)).isFalse();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"171003406, 5", "170000005, 0", "010000000, 9"})
    void checkDigitIsModulo10(String firstNineDigits, int expected) {
        assertThat(NationalId.checkDigit(firstNineDigits + "0")).isEqualTo(expected);
    }

}
