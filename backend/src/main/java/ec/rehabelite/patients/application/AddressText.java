package ec.rehabelite.patients.application;

import ec.rehabelite.catalogs.LocationCatalog;
import ec.rehabelite.catalogs.Place;
import ec.rehabelite.patients.domain.PatientAddress;
import ec.rehabelite.shared.domain.NameCase;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composes the address of a patient (MEJ-03) with the place names of the INEC catalog, shown with a capital initial:
 * "Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. Referencia: frente al
 * parque". An address registered before MEJ-03 shows its former text, which is kept as the reference.
 */
@Component
@RequiredArgsConstructor
public class AddressText {

    private final LocationCatalog locations;

    public AddressView view(PatientAddress address) {
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

    /** The composed text of the address. */
    public String format(PatientAddress address) {
        if (address == null) {
            return "";
        }
        return format(address, address.isComplete() ? locations.findParish(address.parishCode()) : Optional.empty());
    }

    private static String format(PatientAddress address, Optional<Place> place) {
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
