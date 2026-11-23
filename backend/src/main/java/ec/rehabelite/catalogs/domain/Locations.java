package ec.rehabelite.catalogs.domain;

import java.util.List;

/**
 * The catalog resource ({@code catalogs/ec-locations-cge2025.json}), generated from INEC's workbook by
 * {@code tools/catalogs/inec_cge_to_json.py}. Official codes and names, exactly as published.
 */
public record Locations(Source source, List<Province> provinces) {

    public record Source(String publisher, String title, String version, String cutoffDate, String url) {
    }

    public record Province(String code, String name, List<Canton> cantons) {
    }

    public record Canton(String code, String name, List<Parish> parishes) {
    }

    /**
     * @param type {@code URBAN}, {@code RURAL} or {@code SEAT} (the canton seat of a canton without urban
     *             parishes)
     */
    public record Parish(String code, String name, String type) {
    }

}
