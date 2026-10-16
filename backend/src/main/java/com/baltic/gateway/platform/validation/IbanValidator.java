package com.baltic.gateway.platform.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IbanValidator implements ConstraintValidator<ValidIban, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String iban = value.replaceAll("\\s", "").toUpperCase();
        if (!iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char character : rearranged.toCharArray()) {
            if (Character.isDigit(character)) remainder = (remainder * 10 + character - '0') % 97;
            else remainder = (remainder * 100 + character - 'A' + 10) % 97;
        }
        return remainder == 1;
    }
}
