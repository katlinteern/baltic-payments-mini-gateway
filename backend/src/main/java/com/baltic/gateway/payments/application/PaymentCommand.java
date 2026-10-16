package lt.baltic.gateway.payments.application;

import lt.baltic.gateway.platform.money.Money;

public record PaymentCommand(String fromIban, String toIban, String beneficiaryName, Money amount, String reference) {}
