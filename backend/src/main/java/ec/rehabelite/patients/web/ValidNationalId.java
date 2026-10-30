package ec.rehabelite.patients.web;

import ec.rehabelite.patients.domain.NationalId;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Valid Ecuadorian national ID ({@link NationalId}); null is valid, combine with @NotBlank. */
@Documented
@Constraint(validatedBy = ValidNationalId.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface ValidNationalId {

    String message() default "no es una cédula ecuatoriana válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidNationalId, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || NationalId.isValid(value);
        }

    }

}
