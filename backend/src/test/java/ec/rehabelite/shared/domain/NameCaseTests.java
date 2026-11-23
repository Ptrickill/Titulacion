package ec.rehabelite.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameCaseTests {

    @Test
    void removesExtraSpacesAndCapitalizesEachWord() {
        assertThat(NameCase.of("  MARÍA   josé ")).isEqualTo("María José");
        assertThat(NameCase.of("ñusta")).isEqualTo("Ñusta");
        assertThat(NameCase.of("PÉREZ GÓMEZ")).isEqualTo("Pérez Gómez");
    }

    @Test
    void keepsTheParticlesInLowerCaseUnlessTheyStartTheName() {
        assertThat(NameCase.of("SAN MIGUEL DE LOS BANCOS")).isEqualTo("San Miguel de los Bancos");
        assertThat(NameCase.of("pérez DE LA TORRE")).isEqualTo("Pérez de la Torre");
        assertThat(NameCase.of("DEL POZO y ruiz")).isEqualTo("Del Pozo y Ruiz");
        assertThat(NameCase.of("LAS NAVES")).isEqualTo("Las Naves");
    }

    @Test
    void capitalizesAfterAParenthesisOrAHyphen() {
        assertThat(NameCase.of("VICTORIA DEL PORTETE (CAB. EN IRQUIS)")).isEqualTo("Victoria del Portete (Cab. En Irquis)");
        assertThat(NameCase.of("maría-josé")).isEqualTo("María-José");
        assertThat(NameCase.of("ATOCHA – FICOA")).isEqualTo("Atocha – Ficoa");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(NameCase.of(null)).isNull();
        assertThat(NameCase.of("   ")).isEmpty();
    }

}
