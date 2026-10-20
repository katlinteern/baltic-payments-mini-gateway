package com.baltic.gateway.accounts.application.ledger;

import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryView(UUID transactionId, UUID accountId, EntryDirection direction, BigDecimal balanceAfter) {
    static LedgerEntryView of(LedgerEntry entry) {
        return new LedgerEntryView(entry.getTransactionId(), entry.getAccountId(), entry.getDirection(),
                entry.getBalanceAfter());
    }
}
