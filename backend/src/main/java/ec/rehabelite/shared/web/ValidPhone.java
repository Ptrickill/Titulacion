package ec.rehabelite.shared.web;

import ec.rehabelite.shared.domain.PhoneNumber;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ecuadorian mobile or landline number ({@link PhoneNumber}, MEJ-03); null is valid, combine with @NotBlank. */
@Documented
@Constraint(validatedBy = ValidPhone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default PhoneNumber.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || PhoneNumber.isValid(value);
        }

    }

}
