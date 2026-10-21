package com.baltic.gateway.platform.validation;

import com.baltic.gateway.platform.money.Money;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Money.isKnownCurrency(value);
    }
}
