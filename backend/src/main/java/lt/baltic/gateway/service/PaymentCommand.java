package lt.baltic.gateway.service;

import java.math.BigDecimal;

public record PaymentCommand(String fromIban, String toIban, BigDecimal amount, String currency, String reference) {}