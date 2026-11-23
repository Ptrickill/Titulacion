package ec.rehabelite.patients.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Structured address of a patient with the place names and the composed text (MEJ-03). */
@Schema(description = "Dirección estructurada del paciente")
record AddressResponse(
        @Schema(description = "Código de la provincia; null si la dirección está incompleta", example = "17")
        String provinceCode,
        @Schema(description = "Provincia, con mayúscula inicial", example = "Pichincha") String provinceName,
        @Schema(example = "1701") String cantonCode,
        @Schema(example = "Distrito Metropolitano de Quito") String cantonName,
        @Schema(example = "170156") String parishCode,
        @Schema(example = "Conocoto") String parishName,
        @Schema(description = "Calle principal", example = "Av. Ilaló") String mainStreet,
        @Schema(description = "Número (opcional)", example = "N12-34") String streetNumber,
        @Schema(description = "Calle secundaria", example = "Río Pita") String crossStreet,
        @Schema(description = "Referencia; en un paciente registrado antes de MEJ-03, su dirección anterior",
                example = "Frente al parque central") String reference,
        @Schema(description = "Dirección compuesta para mostrar",
                example = "Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. "
                        + "Referencia: Frente al parque central") String formatted,
        @Schema(description = "Si tiene la parroquia, el cantón, la provincia y las dos calles") boolean complete) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "AddressResponse[...]";
    }

}
