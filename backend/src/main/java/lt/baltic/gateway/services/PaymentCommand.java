package lt.baltic.gateway.services;

import java.math.BigDecimal;

public record PaymentCommand(String fromIban, String toIban, String beneficiaryName, BigDecimal amount,
                             String currency, String reference) {
    public PaymentCommand(String fromIban, String toIban, BigDecimal amount, String currency, String reference) {
        this(fromIban, toIban, null, amount, currency, reference);
    }
}