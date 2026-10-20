package com.baltic.gateway.payments.application.execution;

import com.baltic.gateway.platform.money.Money;

public record PaymentCommand(PaymentDetails details, boolean proceedDespiteNameMismatch) {
    public PaymentCommand(String fromIban, String toIban, String beneficiaryName, Money amount, String reference) {
        this(new PaymentDetails(fromIban, toIban, beneficiaryName, amount, reference), false);
    }
}
