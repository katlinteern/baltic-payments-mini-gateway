package com.baltic.gateway.accounts.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.EntryDirection;
import com.baltic.gateway.accounts.domain.LedgerEntry;
import com.baltic.gateway.platform.money.Money;

public record LedgerEntryDto(UUID id, UUID transactionId, EntryDirection direction,
                             BigDecimal amount, String currency,
                             BigDecimal balanceAfter, Instant createdAt) {
    public static LedgerEntryDto from(LedgerEntry entry) {
        String currency = entry.getCurrency();
        return new LedgerEntryDto(entry.getId(), entry.getTransactionId(), entry.getDirection(),
                Money.of(entry.getAmount(), currency).amount(), currency,
                Money.of(entry.getBalanceAfter(), currency).amount(), entry.getCreatedAt());
    }
}
