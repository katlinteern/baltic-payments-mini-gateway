package lt.baltic.gateway.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lt.baltic.gateway.enums.TxStatus;

@Entity
@Table(name = "transactions")
public class PaymentTransaction {
    @Id private UUID id;
    @Column(name = "from_account_id", nullable = false) private UUID fromAccountId;
    @Column(name = "to_account_id", nullable = false) private UUID toAccountId;
    @Column(name = "from_iban", nullable = false) private String fromIban;
    @Column(name = "to_iban", nullable = false) private String toIban;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "to_owner_id", nullable = false) private UUID toOwnerId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false) private String currency;
    private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TxStatus status;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PaymentTransaction() {}
    public PaymentTransaction(Account from, Account to, UUID actor, BigDecimal amount, String currency,
                              String reference, String key) {
        this.id = UUID.randomUUID();
        this.fromAccountId = from.getId();
        this.toAccountId = to.getId();
        this.fromIban = from.getIban();
        this.toIban = to.getIban();
        this.createdBy = actor;
        this.toOwnerId = to.getOwnerId();
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = TxStatus.COMPLETED;
        this.idempotencyKey = key;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getFromIban() { return fromIban; }
    public String getToIban() { return toIban; }
    public UUID getCreatedBy() { return createdBy; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public TxStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
