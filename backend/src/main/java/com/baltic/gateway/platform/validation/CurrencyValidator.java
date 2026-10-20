package com.baltic.gateway.platform.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import com.baltic.gateway.platform.money.Money;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Money.isKnownCurrency(value);
    }
}
