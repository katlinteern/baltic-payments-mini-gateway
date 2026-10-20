package com.baltic.gateway.platform.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import com.baltic.gateway.payments.application.execution.PaymentExecutionService;
import org.junit.jupiter.api.Test;

class IbanValidatorTest {
    private final IbanValidator validator = new IbanValidator();

    @Test void acceptsValidSeedIbansAndNormalizesWhitespaceAndCase() {
        assertTrue(validator.isValid("LT121000011101001000", null));
        assertTrue(validator.isValid("ee38 2200 2210 2014 5685", null));
        assertTrue(validator.isValid("LV80BANK0000435195001", null));
    }

    @Test void rejectsMalformedAndBadChecksumIbans() {
        assertFalse(validator.isValid("not-an-iban", null));
        assertFalse(validator.isValid("LT131000011101001000", null));
    }

    @Test void leavesNullForNotBlankConstraintToHandle() {
        assertTrue(validator.isValid(null, null));
    }

    @Test void isIndependentOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertTrue(validator.isValid("ie29 aibk 9311 5212 3456 78", null));
            assertEquals("IE29AIBK93115212345678", PaymentExecutionService.normalize("ie29 aibk 9311 5212 3456 78"));
        } finally {
            Locale.setDefault(original);
        }
    }
}