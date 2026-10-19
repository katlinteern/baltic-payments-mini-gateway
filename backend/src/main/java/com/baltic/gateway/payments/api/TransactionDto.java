package com.baltic.gateway.payments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.application.TransactionView;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.domain.TxStatus;

/** {@code fromName}, {@code toName} and {@code fromBalanceAfter}/{@code toBalanceAfter} are only filled in when listing the caller's activity. */
public record TransactionDto(UUID id, String fromIban, String toIban, String fromName, String toName,
                             String beneficiaryName, NameCheckResult nameCheck, BigDecimal amount, String currency,
                             String reference, TxStatus status, BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, Instant createdAt) {
    public static TransactionDto from(PaymentTransaction tx) {
        return of(tx, null, null, null, null);
    }

    public static TransactionDto from(TransactionView view) {
        return of(view.transaction(), view.fromName(), view.toName(), view.fromBalanceAfter(),
                view.toBalanceAfter());
    }

    private static TransactionDto of(PaymentTransaction tx, String fromName, String toName, BigDecimal fromBalanceAfter,
                                     BigDecimal toBalanceAfter) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), fromName, toName, tx.getBeneficiaryName(),
                tx.getNameCheck(), tx.getAmount(), tx.getCurrency(), tx.getReference(), tx.getStatus(), fromBalanceAfter,
                toBalanceAfter,
                tx.getCreatedAt());
    }
}
