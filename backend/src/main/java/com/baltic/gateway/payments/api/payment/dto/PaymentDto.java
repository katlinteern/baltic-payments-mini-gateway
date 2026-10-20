package com.baltic.gateway.payments.api.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.application.payment.PaymentView;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.payment.Payment;
import com.baltic.gateway.payments.domain.payment.PaymentStatus;
import com.baltic.gateway.platform.money.Money;

public record PaymentDto(UUID id, PaymentStatus status, String fromIban, String toIban, String beneficiaryName,
                         BigDecimal amount, String currency, String reference, NameCheckResult nameCheck,
                         String registeredName, String verificationCode, Instant expiresAt, UUID transactionId,
                         Instant completedAt, BigDecimal balanceAfter, String failureCode, String failureMessage) {
    public static PaymentDto from(PaymentView view) {
        Payment payment = view.payment();
        Money money = payment.money();
        boolean completed = payment.getStatus() == PaymentStatus.COMPLETED;
        return new PaymentDto(payment.getId(), payment.getStatus(), payment.getFromIban(), payment.getToIban(),
                payment.getBeneficiaryName(), money.amount(), money.currency(), payment.getReference(),
                payment.getNameCheck(), payment.getRegisteredName(), payment.getVerificationCode(),
                payment.getExpiresAt(), payment.getTransactionId(), completed ? payment.getUpdatedAt() : null,
                view.balanceAfter(), payment.getFailureCode(), payment.getFailureMessage());
    }
}
