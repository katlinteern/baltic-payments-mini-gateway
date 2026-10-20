package com.baltic.gateway.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.AccountParty;
import com.baltic.gateway.platform.money.Money;

@Entity
@Table(name = "transactions")
public class PaymentTransaction {
    @Id private UUID id;
    @Column(name = "from_account_id", nullable = false) private UUID fromAccountId;
    @Column(name = "to_account_id", nullable = false) private UUID toAccountId;
    @Column(name = "from_iban", nullable = false) private String fromIban;
    @Column(name = "to_iban", nullable = false) private String toIban;
    @Column(name = "beneficiary_name", nullable = false) private String beneficiaryName;
    @Enumerated(EnumType.STRING) @Column(name = "name_check", nullable = false) private NameCheckResult nameCheck;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "to_owner_id", nullable = false) private UUID toOwnerId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false) private String currency;
    private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TxStatus status;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(AccountParty from, AccountParty to, UUID actor, String beneficiaryName, NameCheckResult nameCheck,
                              Money amount, String reference, String idempotencyKey, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.fromAccountId = from.accountId();
        this.toAccountId = to.accountId();
        this.fromIban = from.iban();
        this.toIban = to.iban();
        this.beneficiaryName = beneficiaryName;
        this.nameCheck = nameCheck;
        this.createdBy = actor;
        this.toOwnerId = to.ownerId();
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.reference = reference;
        this.status = TxStatus.COMPLETED;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    /** The payer: a payment is always created by the owner of the source account. */
    public UUID getCreatedBy() { return createdBy; }
    public UUID getToOwnerId() { return toOwnerId; }
    public String getFromIban() { return fromIban; }
    public String getToIban() { return toIban; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public NameCheckResult getNameCheck() { return nameCheck; }
    public Money money() { return Money.of(amount, currency); }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public TxStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
