package com.baltic.gateway.payments.domain.intent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.platform.money.Money;

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "from_iban", nullable = false, updatable = false) private String fromIban;
    @Column(name = "to_iban", nullable = false, updatable = false) private String toIban;
    @Column(name = "beneficiary_name", nullable = false, updatable = false) private String beneficiaryName;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, updatable = false) private String currency;
    @Column(nullable = false, updatable = false) private String reference;
    @Enumerated(EnumType.STRING) @Column(name = "name_check", nullable = false, updatable = false) private NameCheckResult nameCheck;
    @Column(name = "registered_name", updatable = false) private String registeredName;
    @Column(name = "acknowledged_name_mismatch", nullable = false) private boolean acknowledgedNameMismatch;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentIntentStatus status;
    @Column(name = "verification_code") private String verificationCode;
    @Column(name = "authentication_started_at") private Instant authenticationStartedAt;
    @Column(name = "transaction_id") private UUID transactionId;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "failure_message") private String failureMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected PaymentIntent() {}

    public PaymentIntent(UUID ownerId, String fromIban, String toIban, String beneficiaryName, Money amount,
                         String reference, NameCheckResult nameCheck, String registeredName, Instant now,
                         Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.fromIban = fromIban;
        this.toIban = toIban;
        this.beneficiaryName = beneficiaryName;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.reference = reference;
        this.nameCheck = nameCheck;
        this.registeredName = registeredName;
        this.status = PaymentIntentStatus.READY;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void startAuthentication(boolean acknowledgedNameMismatch, String verificationCode, Instant now, Instant expiresAt) {
        this.acknowledgedNameMismatch = acknowledgedNameMismatch;
        this.status = PaymentIntentStatus.AWAITING_AUTHENTICATION;
        this.verificationCode = verificationCode;
        this.authenticationStartedAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void complete(UUID transactionId, Instant now) {
        end(PaymentIntentStatus.COMPLETED, now);
        this.transactionId = transactionId;
    }

    public void fail(String code, String message, Instant now) {
        end(PaymentIntentStatus.FAILED, now);
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void cancel(Instant now) {
        end(PaymentIntentStatus.CANCELLED, now);
    }

    public boolean isDue(Instant now) {
        return status.isOpen() && now.isAfter(expiresAt);
    }

    public boolean expireIfDue(Instant now) {
        if (!isDue(now)) return false;
        end(PaymentIntentStatus.EXPIRED, now);
        return true;
    }

    private void end(PaymentIntentStatus finalStatus, Instant now) {
        this.status = finalStatus;
        this.verificationCode = null;
        this.updatedAt = now;
    }

    public Money money() { return Money.of(amount, currency); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getFromIban() { return fromIban; }
    public String getToIban() { return toIban; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getReference() { return reference; }
    public NameCheckResult getNameCheck() { return nameCheck; }
    public String getRegisteredName() { return registeredName; }
    public boolean isAcknowledgedNameMismatch() { return acknowledgedNameMismatch; }
    public PaymentIntentStatus getStatus() { return status; }
    public String getVerificationCode() { return verificationCode; }
    public Instant getAuthenticationStartedAt() { return authenticationStartedAt; }
    public UUID getTransactionId() { return transactionId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
