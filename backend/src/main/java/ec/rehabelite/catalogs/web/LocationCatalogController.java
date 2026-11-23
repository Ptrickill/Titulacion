package ec.rehabelite.catalogs.web;

import ec.rehabelite.catalogs.application.ResourceLocationCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/catalogs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Catálogos", description = "Datos de referencia compartidos")
class LocationCatalogController {

    /** Built once: the catalog does not change while the application runs. */
    private final LocationsResponse response;

    LocationCatalogController(ResourceLocationCatalog catalog) {
        this.response = LocationsResponse.of(catalog.locations());
    }

    @GetMapping("/locations")
    @Operation(summary = "Provincias, cantones y parroquias",
            description = "División político-administrativa del Ecuador según el Clasificador Geográfico "
                    + "Estadístico del INEC (edición 2025, corte al 31/12/2024), con los códigos oficiales: provincia "
                    + "de 2 dígitos, cantón de 4 y parroquia de 6, y los nombres oficiales y para mostrar. Cualquier "
                    + "usuario autenticado; no se audita. Se puede guardar en caché un día.")
    @ApiResponse(responseCode = "200", description = "Catálogo completo")
    ResponseEntity<LocationsResponse> locations() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate()).body(response);
    }

}
