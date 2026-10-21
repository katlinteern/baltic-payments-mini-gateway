package com.baltic.gateway.transactions.api.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.transactions.application.history.TransactionView;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import com.baltic.gateway.transactions.domain.transaction.Transaction;
import com.baltic.gateway.transactions.domain.transaction.TransactionStatus;
import com.baltic.gateway.platform.money.Money;

public record TransactionDto(UUID id, String fromIban, String toIban, String fromName, String toName,
                             String beneficiaryName, NameCheckResult nameCheck,
                             BigDecimal amount, String currency,
                             String reference, TransactionStatus status,
                             BigDecimal fromBalanceAfter,
                             BigDecimal toBalanceAfter, Instant createdAt,
                             EntryDirection entry) {
    public static TransactionDto from(Transaction tx) {
        return of(tx, null, null, null, null, null);
    }

    public static TransactionDto from(TransactionView view) {
        return of(view.transaction(), view.fromName(), view.toName(), view.fromBalanceAfter(),
                view.toBalanceAfter(), view.side());
    }

    private static TransactionDto of(Transaction tx, String fromName, String toName, BigDecimal fromBalanceAfter,
                                     BigDecimal toBalanceAfter, EntryDirection side) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), fromName, toName, tx.getBeneficiaryName(),
                tx.getNameCheck(), tx.money().amount(), tx.getCurrency(), tx.getReference(), tx.getStatus(),
                minorUnits(fromBalanceAfter, tx.getCurrency()), minorUnits(toBalanceAfter, tx.getCurrency()),
                tx.getCreatedAt(), side);
    }

    private static BigDecimal minorUnits(BigDecimal amount, String currency) {
        return amount == null ? null : Money.of(amount, currency).amount();
    }
}
