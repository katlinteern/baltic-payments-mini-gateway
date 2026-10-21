package com.baltic.gateway.payments.domain.payment;

import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;

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

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "from_iban", nullable = false, updatable = false)
    private String fromIban;

    @Column(name = "to_iban", nullable = false, updatable = false)
    private String toIban;

    @Column(name = "beneficiary_name", nullable = false, updatable = false)
    private String beneficiaryName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_check", nullable = false, updatable = false)
    private NameCheckResult nameCheck;

    @Column(name = "registered_name", updatable = false)
    private String registeredName;

    @Column(name = "acknowledged_name_mismatch", nullable = false)
    private boolean acknowledgedNameMismatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "authentication_started_at")
    private Instant authenticationStartedAt;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {}

    public Payment(
            UUID ownerId,
            String fromIban,
            String toIban,
            String beneficiaryName,
            Money amount,
            String reference,
            NameCheckResult nameCheck,
            String registeredName,
            Instant now,
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
        this.status = PaymentStatus.READY;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void startAuthentication(
            boolean acknowledgedNameMismatch, String verificationCode, Instant now, Instant expiresAt) {
        requireStatus(PaymentStatus.READY);
        this.acknowledgedNameMismatch = acknowledgedNameMismatch;
        this.status = PaymentStatus.AWAITING_AUTHENTICATION;
        this.verificationCode = verificationCode;
        this.authenticationStartedAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void complete(UUID transactionId, Instant now) {
        requireStatus(PaymentStatus.AWAITING_AUTHENTICATION);
        end(PaymentStatus.COMPLETED, now);
        this.transactionId = transactionId;
    }

    public void fail(String code, String message, Instant now) {
        requireStatus(PaymentStatus.AWAITING_AUTHENTICATION);
        end(PaymentStatus.FAILED, now);
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void cancel(Instant now) {
        if (!status.isOpen()) throw new IllegalStateException("Cannot cancel a payment in status " + status);
        end(PaymentStatus.CANCELLED, now);
    }

    public boolean isDue(Instant now) {
        return status.isOpen() && !now.isBefore(expiresAt);
    }

    public boolean expireIfDue(Instant now) {
        if (!isDue(now)) return false;
        end(PaymentStatus.EXPIRED, now);
        return true;
    }

    private void end(PaymentStatus finalStatus, Instant now) {
        this.status = finalStatus;
        this.verificationCode = null;
        this.updatedAt = now;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected payment status " + expected + " but was " + status);
        }
    }

    public Money money() {
        return Money.of(amount, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getFromIban() {
        return fromIban;
    }

    public String getToIban() {
        return toIban;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getReference() {
        return reference;
    }

    public NameCheckResult getNameCheck() {
        return nameCheck;
    }

    public String getRegisteredName() {
        return registeredName;
    }

    public boolean isAcknowledgedNameMismatch() {
        return acknowledgedNameMismatch;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Instant getAuthenticationStartedAt() {
        return authenticationStartedAt;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
