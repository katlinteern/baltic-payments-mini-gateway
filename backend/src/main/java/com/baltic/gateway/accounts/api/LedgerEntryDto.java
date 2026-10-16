package com.baltic.gateway.accounts.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.EntryDirection;
import com.baltic.gateway.accounts.domain.LedgerEntry;

public record LedgerEntryDto(UUID id, UUID transactionId, EntryDirection direction, BigDecimal amount, String currency,
                             BigDecimal balanceAfter, Instant createdAt) {
    public static LedgerEntryDto from(LedgerEntry entry) {
        return new LedgerEntryDto(entry.getId(), entry.getTransactionId(), entry.getDirection(), entry.getAmount(),
                entry.getCurrency(), entry.getBalanceAfter(), entry.getCreatedAt());
    }
}
