package com.baltic.gateway.payments.application.payment;

import com.baltic.gateway.payments.domain.payment.Payment;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.validation.Iban;

public record PaymentDetails(String fromIban, String toIban, String beneficiaryName, Money amount, String reference) {
    public static PaymentDetails of(Payment payment) {
        return new PaymentDetails(
                payment.getFromIban(),
                payment.getToIban(),
                payment.getBeneficiaryName(),
                payment.money(),
                payment.getReference());
    }

    public PaymentDetails validated() {
        String from = Iban.normalize(fromIban);
        String to = Iban.normalize(toIban);
        if (!amount.isPositive()) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }
        if (from.equals(to)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
        }
        return new PaymentDetails(from, to, beneficiaryName, amount, reference);
    }

    public String summary() {
        return "from=%s to=%s amount=%s %s"
                .formatted(Iban.normalize(fromIban), Iban.normalize(toIban), amount.amount(), amount.currency());
    }
}
