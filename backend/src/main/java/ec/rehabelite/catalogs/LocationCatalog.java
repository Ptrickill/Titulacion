package ec.rehabelite.catalogs;

import java.util.Optional;

/**
 * Provinces, cantons and parishes of Ecuador (INEC, Clasificador Geográfico Estadístico 2025, MEJ-03), with their
 * official codes: province of 2 digits, canton of 4 (its province and its own 2) and parish of 6 (its canton and
 * its own 2). The data never leaves the application: no external service is called.
 */
public interface LocationCatalog {

    /**
     * Whether the parish exists, belongs to the canton and the canton to the province. Used to validate an address.
     */
    boolean isConsistent(String provinceCode, String cantonCode, String parishCode);

    /** The parish with its canton and province, or empty if the code is not in the catalog. */
    Optional<Place> findParish(String parishCode);

}
