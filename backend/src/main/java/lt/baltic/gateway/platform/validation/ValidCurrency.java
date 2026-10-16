package lt.baltic.gateway.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lt.baltic.gateway.platform.money.Money;

@Documented
@Constraint(validatedBy = ValidCurrency.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {
    String message() default "Unknown currency code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCurrency, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Money.isKnownCurrency(value);
        }
    }
}
