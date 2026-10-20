package com.baltic.gateway.payments.api.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.application.transaction.EntrySide;
import com.baltic.gateway.payments.application.transaction.TransactionView;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.transaction.PaymentTransaction;
import com.baltic.gateway.payments.domain.transaction.TransactionStatus;
import com.baltic.gateway.platform.money.Money;

public record TransactionDto(UUID id, String fromIban, String toIban, String fromName, String toName,
                             String beneficiaryName, NameCheckResult nameCheck,
                             BigDecimal amount, String currency,
                             String reference, TransactionStatus status,
                             BigDecimal fromBalanceAfter,
                             BigDecimal toBalanceAfter, Instant createdAt,
                             EntrySide entry) {
    public static TransactionDto from(PaymentTransaction tx) {
        return of(tx, null, null, null, null, null);
    }

    public static TransactionDto from(TransactionView view) {
        return of(view.transaction(), view.fromName(), view.toName(), view.fromBalanceAfter(),
                view.toBalanceAfter(), view.side());
    }

    private static TransactionDto of(PaymentTransaction tx, String fromName, String toName, BigDecimal fromBalanceAfter,
                                     BigDecimal toBalanceAfter, EntrySide side) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), fromName, toName, tx.getBeneficiaryName(),
                tx.getNameCheck(), tx.money().amount(), tx.getCurrency(), tx.getReference(), tx.getStatus(),
                minorUnits(fromBalanceAfter, tx.getCurrency()), minorUnits(toBalanceAfter, tx.getCurrency()),
                tx.getCreatedAt(), side);
    }

    private static BigDecimal minorUnits(BigDecimal amount, String currency) {
        return amount == null ? null : Money.of(amount, currency).amount();
    }
}
