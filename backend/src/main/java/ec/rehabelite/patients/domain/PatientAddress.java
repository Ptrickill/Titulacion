package ec.rehabelite.patients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Structured address of a patient (MEJ-03): the parish with its canton and province (official codes of INEC's
 * Clasificador Geográfico Estadístico), the main street, the number (optional), the cross street and a reference.
 * Text is stored trimmed and a blank number or reference as null.
 * <p>
 * A patient registered before MEJ-03 has only the reference (its former free-text address) and no place nor
 * streets: the address is incomplete ({@link #isComplete()}) until it is completed when the patient is edited.
 */
@Embeddable
public record PatientAddress(
        @Column(name = "province_code", length = 2) @JdbcTypeCode(SqlTypes.CHAR) String provinceCode,
        @Column(name = "canton_code", length = 4) @JdbcTypeCode(SqlTypes.CHAR) String cantonCode,
        @Column(name = "parish_code", length = 6) @JdbcTypeCode(SqlTypes.CHAR) String parishCode,
        @Column(name = "main_street", length = 150) String mainStreet,
        @Column(name = "street_number", length = 20) String streetNumber,
        @Column(name = "cross_street", length = 150) String crossStreet,
        @Column(name = "address_reference", length = 255) String reference) {

    public PatientAddress {
        provinceCode = blankToNull(provinceCode);
        cantonCode = blankToNull(cantonCode);
        parishCode = blankToNull(parishCode);
        mainStreet = blankToNull(mainStreet);
        streetNumber = blankToNull(streetNumber);
        crossStreet = blankToNull(crossStreet);
        reference = blankToNull(reference);
    }

    /** The place and both streets are present; the number and the reference are optional parts. */
    public boolean isComplete() {
        return provinceCode != null && cantonCode != null && parishCode != null && mainStreet != null
                && crossStreet != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().replaceAll("\\s+", " ");
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientAddress[...]";
    }

}
