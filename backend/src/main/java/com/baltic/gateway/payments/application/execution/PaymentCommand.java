package com.baltic.gateway.payments.application.execution;

import com.baltic.gateway.platform.money.Money;

/**
 * @param proceedDespiteNameMismatch the payer saw a failed payee verification and chose to send anyway
 */
public record PaymentCommand(String fromIban, String toIban, String beneficiaryName, Money amount, String reference,
                             boolean proceedDespiteNameMismatch) {
    public PaymentCommand(String fromIban, String toIban, String beneficiaryName, Money amount, String reference) {
        this(fromIban, toIban, beneficiaryName, amount, reference, false);
    }
}
