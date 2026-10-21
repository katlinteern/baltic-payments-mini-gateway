package com.baltic.gateway.transactions.domain.transaction;

import com.baltic.gateway.accounts.domain.account.AccountParty;
import com.baltic.gateway.platform.money.Money;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(name = "from_iban", nullable = false)
    private String fromIban;

    @Column(name = "to_iban", nullable = false)
    private String toIban;

    @Column(name = "from_name", nullable = false)
    private String fromName;

    @Column(name = "to_name", nullable = false)
    private String toName;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_check", nullable = false)
    private NameCheckResult nameCheck;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "to_owner_id", nullable = false)
    private UUID toOwnerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    public Transaction(
            AccountParty from,
            AccountParty to,
            UUID actor,
            String beneficiaryName,
            NameCheckResult nameCheck,
            Money amount,
            String reference,
            String idempotencyKey,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.fromAccountId = from.accountId();
        this.toAccountId = to.accountId();
        this.fromIban = from.iban();
        this.toIban = to.iban();
        this.fromName = from.holderName();
        this.toName = to.holderName();
        this.beneficiaryName = beneficiaryName;
        this.nameCheck = nameCheck;
        this.createdBy = actor;
        this.toOwnerId = to.ownerId();
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.reference = reference;
        this.status = TransactionStatus.COMPLETED;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    public Money money() {
        return Money.of(amount, currency);
    }
}
