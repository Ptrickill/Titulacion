package ec.rehabelite.catalogs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.catalogs.Place;
import ec.rehabelite.catalogs.domain.Locations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MEJ-03: the catalog resource generated from INEC's Clasificador Geográfico Estadístico 2025 (cut-off 31/12/2024).
 * The counts are those documented in docs/catalogs/ubicaciones.md.
 */
class ResourceLocationCatalogTests {

    private final ResourceLocationCatalog catalog = new ResourceLocationCatalog();
    private final Locations locations = catalog.locations();

    @Test
    void hasTheProvincesCantonsAndParishesOfTheOfficialEdition() {
        assertThat(locations.source().version()).isEqualTo("CGE 2025");
        assertThat(locations.source().cutoffDate()).isEqualTo("2024-12-31");
        assertThat(locations.provinces()).hasSize(24);
        assertThat(cantons()).hasSize(222);
        assertThat(parishes()).hasSize(1255);
        assertThat(parishes().stream().filter(p -> p.type().equals("URBAN"))).hasSize(268);
        assertThat(parishes().stream().filter(p -> p.type().equals("RURAL"))).hasSize(824);
        assertThat(parishes().stream().filter(p -> p.type().equals("SEAT"))).hasSize(163);
    }

    @Test
    void everyCodeIsUniqueAndNestedInItsParent() {
        Set<String> codes = new HashSet<>();
        for (Locations.Province province : locations.provinces()) {
            assertThat(province.code()).matches("\\d{2}");
            assertThat(codes.add(province.code())).isTrue();
            assertThat(province.cantons()).isNotEmpty();
            for (Locations.Canton canton : province.cantons()) {
                assertThat(canton.code()).matches("\\d{4}").startsWith(province.code());
                assertThat(codes.add(canton.code())).isTrue();
                assertThat(canton.parishes()).isNotEmpty();
                for (Locations.Parish parish : canton.parishes()) {
                    assertThat(parish.code()).matches("\\d{6}").startsWith(canton.code());
                    assertThat(codes.add(parish.code())).isTrue();
                    assertThat(parish.name()).isNotBlank().doesNotStartWith("*");
                }
            }
        }
    }

    @Test
    void leavesOutTheZonesUnderStudyAndTheJurisdictionsThatChanged() {
        assertThat(locations.provinces()).noneMatch(p -> p.code().equals("90"));
        // Chordeleg was a parish of Gualaceo (010351) before becoming a canton (0111)
        assertThat(catalog.findParish("010351")).isEmpty();
        assertThat(cantons()).anyMatch(c -> c.code().equals("0111") && c.name().equals("CHORDELEG"));
        // La Concordia belongs to Santo Domingo de los Tsáchilas (2302), no longer to Esmeraldas (0808)
        assertThat(cantons()).noneMatch(c -> c.code().equals("0808"));
        assertThat(cantons()).anyMatch(c -> c.code().equals("2302"));
    }

    @Test
    void theCantonSeatIsAParishOnlyWhereThereAreNoUrbanParishes() {
        // Quito has urban parishes: its seat (170150) is not offered
        assertThat(catalog.findParish("170150")).isEmpty();
        assertThat(catalog.findParish("170112")).map(Place::parishName).contains("IÑAQUITO");
        // Girón has none: its seat is the urban option
        assertThat(catalog.findParish("010250")).map(Place::parishName).contains("GIRÓN (CABECERA CANTONAL)");
    }

    @Test
    void checksThatTheParishBelongsToTheCantonAndTheCantonToTheProvince() {
        assertThat(catalog.isConsistent("17", "1701", "170156")).isTrue();
        assertThat(catalog.isConsistent("09", "1701", "170156")).isFalse();
        assertThat(catalog.isConsistent("17", "1702", "170156")).isFalse();
        assertThat(catalog.isConsistent("17", "1701", "170199")).isFalse();
        assertThat(catalog.isConsistent(null, null, null)).isFalse();
    }

    @Test
    void givesThePlaceWithItsOfficialNamesAndItsDisplayName() {
        Place place = catalog.findParish("170156").orElseThrow();

        assertThat(place.provinceName()).isEqualTo("PICHINCHA");
        assertThat(place.cantonName()).isEqualTo("DISTRITO METROPOLITANO DE QUITO");
        assertThat(place.parishName()).isEqualTo("CONOCOTO");
        assertThat(place.displayName()).isEqualTo("Conocoto, Distrito Metropolitano de Quito, Pichincha");
        assertThat(catalog.findParish("171551").map(Place::displayName)).isEmpty();
    }

    private List<Locations.Canton> cantons() {
        return locations.provinces().stream().flatMap(p -> p.cantons().stream()).toList();
    }

    private List<Locations.Parish> parishes() {
        return cantons().stream().flatMap(c -> c.parishes().stream()).toList();
    }

}
