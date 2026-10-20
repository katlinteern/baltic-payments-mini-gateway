package com.baltic.gateway.payments.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.application.EntrySide;
import com.baltic.gateway.payments.application.TransactionView;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.domain.TxStatus;
import com.baltic.gateway.platform.money.Money;

/**
 * {@code entry} says which half of an own-account transfer this row is. {@code fromName}, {@code toName} and
 * {@code fromBalanceAfter}/{@code toBalanceAfter} are only filled in when listing the caller's activity.
 * Amounts are JSON numbers with the scale of the currency's minor units (for example 10.00).
 */
public record TransactionDto(UUID id, String fromIban, String toIban, String fromName, String toName,
                             String beneficiaryName, NameCheckResult nameCheck,
                             BigDecimal amount, String currency,
                             String reference, TxStatus status,
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
