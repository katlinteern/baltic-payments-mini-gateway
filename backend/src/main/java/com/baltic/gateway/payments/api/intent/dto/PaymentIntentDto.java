package com.baltic.gateway.payments.api.intent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.application.intent.IntentView;
import com.baltic.gateway.payments.domain.intent.IntentStatus;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.platform.money.Money;

/**
 * A payment as the payer sees it while preparing and authorising it. {@code registeredName} is present only for a close
 * name match; {@code verificationCode} only while the authentication app is being asked; {@code transactionId},
 * {@code completedAt} and {@code balanceAfter} only once the payment has been executed.
 */
public record PaymentIntentDto(UUID id, IntentStatus status, String fromIban, String toIban, String beneficiaryName,
                               BigDecimal amount, String currency, String reference, NameCheckResult nameCheck,
                               String registeredName, String verificationCode, Instant expiresAt, UUID transactionId,
                               Instant completedAt, BigDecimal balanceAfter, String failureCode,
                               String failureMessage) {
    public static PaymentIntentDto from(IntentView view) {
        PaymentIntent intent = view.intent();
        Money money = intent.money();
        boolean completed = intent.getStatus() == IntentStatus.COMPLETED;
        return new PaymentIntentDto(intent.getId(), intent.getStatus(), intent.getFromIban(), intent.getToIban(),
                intent.getBeneficiaryName(), money.amount(), money.currency(), intent.getReference(),
                intent.getNameCheck(), intent.getRegisteredName(), intent.getVerificationCode(), intent.getExpiresAt(),
                intent.getTransactionId(), completed ? intent.getUpdatedAt() : null, view.balanceAfter(),
                intent.getFailureCode(), intent.getFailureMessage());
    }
}
