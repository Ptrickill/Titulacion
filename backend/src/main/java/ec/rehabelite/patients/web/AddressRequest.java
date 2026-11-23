package ec.rehabelite.patients.web;

import ec.rehabelite.catalogs.LocationCatalog;
import ec.rehabelite.patients.domain.PatientAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Structured address of a patient (MEJ-03). The place uses the official codes of INEC's Clasificador Geográfico
 * Estadístico ({@code GET /api/catalogs/locations}); the parish must belong to the canton and the canton to the
 * province. Every part is required except the number. The address is never sent to an external service.
 */
@Schema(description = "Dirección estructurada: parroquia, cantón y provincia del catálogo del INEC, y las calles")
@AddressRequest.ConsistentPlace
record AddressRequest(
        @Schema(description = "Código de la provincia (2 dígitos)", example = "17")
        @NotBlank @Pattern(regexp = "^\\d{2}$", message = "debe tener 2 dígitos") String provinceCode,

        @Schema(description = "Código del cantón (4 dígitos)", example = "1701")
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "debe tener 4 dígitos") String cantonCode,

        @Schema(description = "Código de la parroquia (6 dígitos)", example = "170156")
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String parishCode,

        @Schema(description = "Calle principal", example = "Av. Ilaló")
        @NotBlank @Size(max = 150) String mainStreet,

        @Schema(description = "Número de casa (opcional)", example = "N12-34")
        @Size(max = 20) String streetNumber,

        @Schema(description = "Calle secundaria", example = "Río Pita")
        @NotBlank @Size(max = 150) String crossStreet,

        @Schema(description = "Referencia", example = "Frente al parque central")
        @NotBlank @Size(max = 255) String reference) {

    static final String INCONSISTENT_PLACE = "la parroquia debe pertenecer al cantón y el cantón a la provincia";

    PatientAddress toAddress() {
        return new PatientAddress(provinceCode, cantonCode, parishCode, mainStreet, streetNumber, crossStreet,
                reference);
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "AddressRequest[...]";
    }

    /** The parish, the canton and the province exist in the catalog and are nested. */
    @Documented
    @Constraint(validatedBy = ConsistentPlaceValidator.class)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ConsistentPlace {

        String message() default INCONSISTENT_PLACE;

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

    }

    /** Created by Spring's validator factory, so the catalog is injected. */
    static class ConsistentPlaceValidator implements ConstraintValidator<ConsistentPlace, AddressRequest> {

        private final LocationCatalog locations;

        ConsistentPlaceValidator(LocationCatalog locations) {
            this.locations = locations;
        }

        @Override
        public boolean isValid(AddressRequest address, ConstraintValidatorContext context) {
            // Missing or malformed codes are reported by their own constraints
            if (address == null || address.provinceCode() == null || address.cantonCode() == null
                    || address.parishCode() == null) {
                return true;
            }
            if (locations.isConsistent(address.provinceCode(), address.cantonCode(), address.parishCode())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("parishCode").addConstraintViolation();
            return false;
        }

    }

}
