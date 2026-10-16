package com.baltic.gateway.platform.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Monetary amount with its ISO 4217 currency; the scale always matches the currency's minor units. */
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new IllegalArgumentException("Amount has more than " + digits + " decimals for " + currency);
        }
        amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static boolean isKnownCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean sameCurrency(Money other) {
        return currency.equals(other.currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!sameCurrency(other)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
