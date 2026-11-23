package ec.rehabelite.patients.application;

/**
 * Address of a patient ready to show (MEJ-03): the stored parts, the names of its parish, canton and province with
 * a capital initial (null if incomplete), the composed text and whether it is complete.
 */
public record AddressView(
        String provinceCode,
        String provinceName,
        String cantonCode,
        String cantonName,
        String parishCode,
        String parishName,
        String mainStreet,
        String streetNumber,
        String crossStreet,
        String reference,
        String formatted,
        boolean complete) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "AddressView[...]";
    }

}
