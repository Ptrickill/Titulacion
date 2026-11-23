package ec.rehabelite.catalogs;

import ec.rehabelite.shared.domain.NameCase;

/**
 * A parish with its canton and province. The names are the official ones, as INEC publishes them (in capitals);
 * {@link #displayName()} shows them with a capital initial.
 */
public record Place(
        String provinceCode,
        String provinceName,
        String cantonCode,
        String cantonName,
        String parishCode,
        String parishName) {

    /** "Conocoto, Distrito Metropolitano de Quito, Pichincha". */
    public String displayName() {
        return NameCase.of(parishName) + ", " + NameCase.of(cantonName) + ", " + NameCase.of(provinceName);
    }

}
