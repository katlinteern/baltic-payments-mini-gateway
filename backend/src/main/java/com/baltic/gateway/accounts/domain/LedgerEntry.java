package com.baltic.gateway.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.platform.money.Money;

/** Immutable posting on an account. Every movement of funds produces a matching debit and credit entry. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id private UUID id;
    @Column(name = "transaction_id", nullable = false, updatable = false) private UUID transactionId;
    @Column(name = "account_id", nullable = false, updatable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private EntryDirection direction;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, updatable = false) private String currency;
    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal balanceAfter;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(UUID transactionId, Account account, EntryDirection direction, Money amount, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = account.getId();
        this.direction = direction;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.balanceAfter = account.getBalance();
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public EntryDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
