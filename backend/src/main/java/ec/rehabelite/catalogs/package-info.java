/**
 * Catalogs.
 * <p>
 * Reference data shared by other modules (MEJ-03): the political-administrative division of Ecuador (provinces,
 * cantons and parishes of INEC's Clasificador Geográfico Estadístico), read from a resource of the application.
 * Only types in this root package are the module's public API; web, application and domain are internal.
 */
@ApplicationModule(displayName = "Catalogs")
package ec.rehabelite.catalogs;

import org.springframework.modulith.ApplicationModule;
