package com.baltic.gateway.accounts.application.ledger;

import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import java.math.BigDecimal;
import java.util.UUID;

public record Posting(UUID transactionId, UUID accountId, EntryDirection direction, BigDecimal balanceAfter) {
    static Posting of(LedgerEntry entry) {
        return new Posting(entry.getTransactionId(), entry.getAccountId(), entry.getDirection(), entry.getBalanceAfter());
    }
}
