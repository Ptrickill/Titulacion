package ec.rehabelite.catalogs;

import ec.rehabelite.shared.domain.NameCase;
import ec.rehabelite.shared.domain.PostalAddress;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composes an address (MEJ-03) with the place names of the INEC catalog, shown with a capital initial:
 * "Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. Referencia: frente al
 * parque". An address registered before MEJ-03 shows its former text, which is kept as the reference. Public API of
 * the catalogs module, used by patients and staff (MEJ-02).
 */
@Component
@RequiredArgsConstructor
public class AddressFormatter {

    private final LocationCatalog locations;

    public AddressView view(PostalAddress address) {
        if (address == null) {
            return new AddressView(null, null, null, null, null, null, null, null, null, null, "", false);
        }
        Optional<Place> place = address.isComplete() ? locations.findParish(address.parishCode()) : Optional.empty();
        String provinceName = place.map(p -> NameCase.of(p.provinceName())).orElse(null);
        String cantonName = place.map(p -> NameCase.of(p.cantonName())).orElse(null);
        String parishName = place.map(p -> NameCase.of(p.parishName())).orElse(null);
        return new AddressView(address.provinceCode(), provinceName, address.cantonCode(), cantonName,
                address.parishCode(), parishName, address.mainStreet(), address.streetNumber(), address.crossStreet(),
                address.reference(), format(address, place), address.isComplete() && place.isPresent());
    }

    /** The composed text of the address; empty for no address. */
    public String format(PostalAddress address) {
        if (address == null) {
            return "";
        }
        return format(address, address.isComplete() ? locations.findParish(address.parishCode()) : Optional.empty());
    }

    private static String format(PostalAddress address, Optional<Place> place) {
        if (!address.isComplete() || place.isEmpty()) {
            return address.reference() == null ? "" : address.reference();
        }
        StringBuilder text = new StringBuilder(address.mainStreet());
        if (address.streetNumber() != null) {
            text.append(' ').append(address.streetNumber());
        }
        text.append(" y ").append(address.crossStreet()).append(", ").append(place.get().displayName());
        if (address.reference() != null) {
            text.append(". Referencia: ").append(address.reference());
        }
        return text.toString();
    }

}
