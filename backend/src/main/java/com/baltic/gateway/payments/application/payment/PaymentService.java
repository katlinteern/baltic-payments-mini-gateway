package com.baltic.gateway.payments.application.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.accounts.application.transfer.TransferService;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import com.baltic.gateway.payments.application.payee.PayeeVerificationService;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import com.baltic.gateway.payments.domain.payment.Payment;
import com.baltic.gateway.payments.domain.payment.PaymentStatus;
import com.baltic.gateway.payments.infrastructure.payment.PaymentRepository;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.validation.Iban;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final AccountService accounts;
    private final TransferService transfers;
    private final PayeeVerificationService payeeVerification;
    private final AuditService audit;
    private final GatewayMetrics metrics;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration paymentTtl;
    private final Duration authenticationTtl;

    public PaymentService(PaymentRepository payments, AccountService accounts, TransferService transfers,
                          PayeeVerificationService payeeVerification, AuditService audit, GatewayMetrics metrics,
                          ObjectProvider<StrongAuthenticator> authenticator,
                          PlatformTransactionManager transactionManager, Clock clock,
                          @Value("${app.payments.ttl}") Duration paymentTtl,
                          @Value("${app.payments.authentication-ttl}") Duration authenticationTtl) {
        this.payments = payments;
        this.accounts = accounts;
        this.transfers = transfers;
        this.payeeVerification = payeeVerification;
        this.audit = audit;
        this.metrics = metrics;
        this.authenticator = authenticator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.paymentTtl = paymentTtl;
        this.authenticationTtl = authenticationTtl;
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Unknown payment");
    }

    public PaymentView create(UserPrincipal actor, PaymentDetails details, String ip) {
        Money amount = details.amount();
        try {
            String fromIban = Iban.normalize(details.fromIban());
            String toIban = Iban.normalize(details.toIban());
            if (!amount.isPositive()) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
            }
            if (fromIban.equals(toIban)) {
                throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
            }
            return transactionTemplate.execute(status -> {
                accounts.requireOwnedBy(fromIban, actor.id());
                transfers.checkTransferable(fromIban, toIban, amount);
                var verification = payeeVerification.verify(toIban, details.beneficiaryName());
                Instant now = clock.instant();
                var payment = payments.save(new Payment(actor.id(), fromIban, toIban, details.beneficiaryName(),
                        amount, details.reference(), verification.result(), verification.registeredName(), now,
                        now.plus(paymentTtl)));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_CREATED, ip,
                        "payment=%s %s nameCheck=%s".formatted(payment.getId(), details.summary(), verification.result())));
                return new PaymentView(payment, null);
            });
        } catch (BusinessException ex) {
            rejected(actor, amount, ex, ip, details.summary());
            throw ex;
        }
    }

    public PaymentView startConfirmation(UserPrincipal actor, UUID id, boolean acknowledgeNameMismatch, String ip) {
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (strongAuthenticator == null) {
            throw new BusinessException(ErrorCode.STRONG_AUTHENTICATION_UNAVAILABLE,
                    "No authentication provider is configured, so payments cannot be confirmed");
        }
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = payments.findByIdAndOwnerIdForUpdate(id, actor.id())
                        .orElseThrow(PaymentService::notFound);
                Instant now = clock.instant();
                payment.expireIfDue(now);
                if (payment.getStatus() == PaymentStatus.AWAITING_AUTHENTICATION) return new PaymentView(payment, null);
                requireStatus(payment, PaymentStatus.READY);
                if (payment.getNameCheck() == NameCheckResult.NO_MATCH && !acknowledgeNameMismatch) {
                    throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                            "The beneficiary name does not match the holder of the destination account");
                }
                var challenge = strongAuthenticator.start(now);
                payment.startAuthentication(acknowledgeNameMismatch, challenge.verificationCode(), now,
                        now.plus(authenticationTtl));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_STARTED, ip,
                        "payment=%s".formatted(payment.getId())));
                return new PaymentView(payment, null);
            });
        } catch (BusinessException ex) {
            rejectedConfirmation(actor, id, ex, ip);
            throw ex;
        }
    }

    public PaymentView cancel(UserPrincipal actor, UUID id, String ip) {
        return transactionTemplate.execute(status -> {
            Payment payment = payments.findByIdAndOwnerIdForUpdate(id, actor.id())
                    .orElseThrow(PaymentService::notFound);
            payment.expireIfDue(clock.instant());
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT, "The payment has already been executed");
            }
            if (payment.getStatus().isOpen()) {
                payment.cancel(clock.instant());
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_CANCELLED, ip,
                        "payment=%s".formatted(payment.getId())));
            }
            return new PaymentView(payment, null);
        });
    }

    private void rejected(UserPrincipal actor, Money amount, BusinessException ex, String ip, String subject) {
        if (!ex.getCode().isRuleViolation()) return;
        if (amount != null) metrics.paymentRejected(amount, ex.getCode());
        audit.recordIndependent(AuditEvent.by(actor, AuditAction.PAYMENT_REJECTED, ip,
                "reason=%s %s".formatted(ex.getCode(), subject)));
    }

    private void rejectedConfirmation(UserPrincipal actor, UUID id, BusinessException ex, String ip) {
        var payment = payments.findByIdAndOwnerId(id, actor.id());
        String subject = payment.map(found -> "payment=%s %s".formatted(id, PaymentDetails.of(found).summary()))
                .orElse("payment=" + id);
        rejected(actor, payment.map(Payment::money).orElse(null), ex, ip, subject);
    }

    private static void requireStatus(Payment payment, PaymentStatus expected) {
        if (payment.getStatus() != expected) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The payment is " + payment.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " and cannot be confirmed");
        }
    }
}
