package ec.rehabelite.catalogs.web;

import ec.rehabelite.catalogs.domain.Locations;
import ec.rehabelite.shared.domain.NameCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The location catalog for the address lists. Each place keeps its official INEC name ({@code name}) and the name
 * to show ({@code displayName}, with a capital initial, MEJ-03).
 */
@Schema(description = "División político-administrativa del Ecuador (INEC, Clasificador Geográfico Estadístico)")
record LocationsResponse(
        @Schema(example = "INEC - Clasificador Geográfico Estadístico 2025") String source,
        @Schema(example = "CGE 2025") String version,
        @Schema(description = "Fecha de corte de la información", example = "2024-12-31") String cutoffDate,
        List<ProvinceOption> provinces) {

    record ProvinceOption(
            @Schema(example = "17") String code,
            @Schema(description = "Nombre oficial", example = "PICHINCHA") String name,
            @Schema(description = "Nombre para mostrar", example = "Pichincha") String displayName,
            List<CantonOption> cantons) {
    }

    record CantonOption(
            @Schema(example = "1701") String code,
            @Schema(example = "DISTRITO METROPOLITANO DE QUITO") String name,
            @Schema(example = "Distrito Metropolitano de Quito") String displayName,
            List<ParishOption> parishes) {
    }

    record ParishOption(
            @Schema(example = "170156") String code,
            @Schema(example = "CONOCOTO") String name,
            @Schema(example = "Conocoto") String displayName,
            @Schema(description = "URBAN, RURAL o SEAT (cabecera cantonal de un cantón sin parroquias urbanas)",
                    example = "RURAL") String type) {
    }

    static LocationsResponse of(Locations locations) {
        Locations.Source source = locations.source();
        return new LocationsResponse(source.publisher() + " - " + source.title(), source.version(),
                source.cutoffDate(), locations.provinces().stream().map(LocationsResponse::province).toList());
    }

    private static ProvinceOption province(Locations.Province province) {
        return new ProvinceOption(province.code(), province.name(), NameCase.of(province.name()),
                province.cantons().stream().map(LocationsResponse::canton).toList());
    }

    private static CantonOption canton(Locations.Canton canton) {
        return new CantonOption(canton.code(), canton.name(), NameCase.of(canton.name()),
                canton.parishes().stream()
                        .map(p -> new ParishOption(p.code(), p.name(), NameCase.of(p.name()), p.type()))
                        .toList());
    }

}
