package com.baltic.gateway.payments.application.payment;

import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.validation.Iban;

public record PaymentDetails(String fromIban, String toIban, String beneficiaryName, Money amount, String reference) {
    public static PaymentDetails of(PaymentIntent intent) {
        return new PaymentDetails(intent.getFromIban(), intent.getToIban(), intent.getBeneficiaryName(), intent.money(),
                intent.getReference());
    }

    public String summary() {
        return "from=%s to=%s amount=%s %s".formatted(Iban.normalize(fromIban), Iban.normalize(toIban), amount.amount(),
                amount.currency());
    }
}
