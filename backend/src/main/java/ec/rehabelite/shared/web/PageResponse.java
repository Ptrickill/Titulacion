package ec.rehabelite.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of results; a stable JSON shape instead of serializing Spring Data's {@link Page}. */
@Schema(description = "Página de resultados")
public record PageResponse<T>(
        @Schema(description = "Elementos de la página") List<T> content,
        @Schema(description = "Número de página, desde 0", example = "0") int page,
        @Schema(description = "Tamaño de página", example = "10") int size,
        @Schema(description = "Total de elementos que coinciden", example = "42") long totalElements,
        @Schema(description = "Total de páginas", example = "5") int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

}
