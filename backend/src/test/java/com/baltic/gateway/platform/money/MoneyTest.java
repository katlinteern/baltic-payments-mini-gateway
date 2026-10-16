package lt.baltic.gateway.platform.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test void normalizesScaleToCurrencyMinorUnits() {
        assertEquals(new BigDecimal("10.00"), Money.of(new BigDecimal("10"), "EUR").amount());
        assertEquals(new BigDecimal("10"), Money.of(new BigDecimal("10.0"), "JPY").amount());
    }

    @Test void equalityIgnoresInputScale() {
        assertEquals(Money.of(new BigDecimal("1.5"), "EUR"), Money.of(new BigDecimal("1.50"), "EUR"));
    }

    @Test void rejectsSubMinorUnitPrecisionAndUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("1.001"), "EUR"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("1.5"), "JPY"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ONE, "ZZZ"));
        assertFalse(Money.isKnownCurrency("ZZZ"));
        assertTrue(Money.isKnownCurrency("EUR"));
    }

    @Test void arithmeticRequiresSameCurrency() {
        var ten = Money.of(BigDecimal.TEN, "EUR");
        assertEquals(Money.of(new BigDecimal("12.50"), "EUR"), ten.plus(Money.of(new BigDecimal("2.50"), "EUR")));
        assertEquals(Money.of(new BigDecimal("7.50"), "EUR"), ten.minus(Money.of(new BigDecimal("2.50"), "EUR")));
        assertTrue(ten.isLessThan(Money.of(new BigDecimal("10.01"), "EUR")));
        assertThrows(IllegalArgumentException.class, () -> ten.plus(Money.of(BigDecimal.ONE, "USD")));
    }
}
