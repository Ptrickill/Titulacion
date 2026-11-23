package ec.rehabelite.catalogs.application;

import ec.rehabelite.catalogs.LocationCatalog;
import ec.rehabelite.catalogs.Place;
import ec.rehabelite.catalogs.domain.Locations;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the location catalog from {@value #RESOURCE} once, at startup, and keeps it in memory: it changes only
 * with a new INEC edition, which is a new resource. Nothing is fetched from outside the application.
 */
@Service
public class ResourceLocationCatalog implements LocationCatalog {

    static final String RESOURCE = "catalogs/ec-locations-cge2025.json";

    private static final Logger log = LoggerFactory.getLogger(ResourceLocationCatalog.class);

    private final Locations locations;
    private final Map<String, Place> placesByParish = new HashMap<>();

    public ResourceLocationCatalog() {
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            locations = mapper.readValue(input, Locations.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the location catalog " + RESOURCE, ex);
        }
        for (Locations.Province province : locations.provinces()) {
            for (Locations.Canton canton : province.cantons()) {
                for (Locations.Parish parish : canton.parishes()) {
                    placesByParish.put(parish.code(), new Place(province.code(), province.name(), canton.code(),
                            canton.name(), parish.code(), parish.name()));
                }
            }
        }
        log.info("Location catalog loaded version={} provinces={} parishes={}", locations.source().version(),
                locations.provinces().size(), placesByParish.size());
    }

    /** The whole tree, for the address lists of the frontend. */
    public Locations locations() {
        return locations;
    }

    @Override
    public boolean isConsistent(String provinceCode, String cantonCode, String parishCode) {
        Place place = parishCode == null ? null : placesByParish.get(parishCode);
        return place != null && place.cantonCode().equals(cantonCode) && place.provinceCode().equals(provinceCode);
    }

    @Override
    public Optional<Place> findParish(String parishCode) {
        return Optional.ofNullable(parishCode == null ? null : placesByParish.get(parishCode));
    }

}
