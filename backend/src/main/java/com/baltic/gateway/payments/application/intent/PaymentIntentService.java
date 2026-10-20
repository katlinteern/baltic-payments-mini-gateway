package com.baltic.gateway.payments.application.intent;

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
import com.baltic.gateway.payments.application.payment.PaymentDetails;
import com.baltic.gateway.payments.application.payee.PayeeVerificationService;
import com.baltic.gateway.payments.domain.intent.PaymentIntentStatus;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.infrastructure.intent.PaymentIntentRepository;
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
public class PaymentIntentService {
    private final PaymentIntentRepository intents;
    private final AccountService accounts;
    private final TransferService transfers;
    private final PayeeVerificationService payeeVerification;
    private final AuditService audit;
    private final GatewayMetrics metrics;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration intentTtl;
    private final Duration authenticationTtl;

    public PaymentIntentService(PaymentIntentRepository intents, AccountService accounts, TransferService transfers,
                                PayeeVerificationService payeeVerification, AuditService audit, GatewayMetrics metrics,
                                ObjectProvider<StrongAuthenticator> authenticator,
                                PlatformTransactionManager transactionManager, Clock clock,
                                @Value("${app.payments.intent-ttl}") Duration intentTtl,
                                @Value("${app.payments.authentication-ttl}") Duration authenticationTtl) {
        this.intents = intents;
        this.accounts = accounts;
        this.transfers = transfers;
        this.payeeVerification = payeeVerification;
        this.audit = audit;
        this.metrics = metrics;
        this.authenticator = authenticator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.intentTtl = intentTtl;
        this.authenticationTtl = authenticationTtl;
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Unknown payment");
    }

    public PaymentIntentView create(UserPrincipal actor, PaymentDetails details, String ip) {
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
            return transaction.execute(status -> {
                accounts.requireOwnedBy(fromIban, actor.id());
                transfers.checkTransferable(fromIban, toIban, amount);
                var verification = payeeVerification.verify(toIban, details.beneficiaryName());
                Instant now = clock.instant();
                var intent = intents.save(new PaymentIntent(actor.id(), fromIban, toIban, details.beneficiaryName(),
                        amount, details.reference(), verification.result(), verification.registeredName(), now,
                        now.plus(intentTtl)));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_INTENT_CREATED, ip,
                        "intent=%s %s nameCheck=%s".formatted(intent.getId(), details.summary(), verification.result())));
                return new PaymentIntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejected(actor, amount, ex, ip, details.summary());
            throw ex;
        }
    }

    public PaymentIntentView startConfirmation(UserPrincipal actor, UUID id, boolean acknowledgeNameMismatch, String ip) {
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (strongAuthenticator == null) {
            throw new BusinessException(ErrorCode.STRONG_AUTHENTICATION_UNAVAILABLE,
                    "No authentication provider is configured, so payments cannot be confirmed");
        }
        try {
            return transaction.execute(status -> {
                PaymentIntent intent = intents.findByIdAndOwnerIdForUpdate(id, actor.id())
                        .orElseThrow(PaymentIntentService::notFound);
                Instant now = clock.instant();
                intent.expireIfDue(now);
                if (intent.getStatus() == PaymentIntentStatus.AWAITING_AUTHENTICATION) return new PaymentIntentView(intent, null);
                requireStatus(intent, PaymentIntentStatus.READY);
                if (intent.getNameCheck() == NameCheckResult.NO_MATCH && !acknowledgeNameMismatch) {
                    throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                            "The beneficiary name does not match the holder of the destination account");
                }
                var challenge = strongAuthenticator.start(now);
                intent.startAuthentication(acknowledgeNameMismatch, challenge.verificationCode(), now,
                        now.plus(authenticationTtl));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_STARTED, ip,
                        "intent=%s".formatted(intent.getId())));
                return new PaymentIntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejectedConfirmation(actor, id, ex, ip);
            throw ex;
        }
    }

    public PaymentIntentView cancel(UserPrincipal actor, UUID id, String ip) {
        return transaction.execute(status -> {
            PaymentIntent intent = intents.findByIdAndOwnerIdForUpdate(id, actor.id())
                    .orElseThrow(PaymentIntentService::notFound);
            intent.expireIfDue(clock.instant());
            if (intent.getStatus() == PaymentIntentStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT, "The payment has already been executed");
            }
            if (intent.getStatus().isOpen()) {
                intent.cancel(clock.instant());
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_INTENT_CANCELLED, ip,
                        "intent=%s".formatted(intent.getId())));
            }
            return new PaymentIntentView(intent, null);
        });
    }

    private void rejected(UserPrincipal actor, Money amount, BusinessException ex, String ip, String subject) {
        if (!ex.getCode().isRuleViolation()) return;
        if (amount != null) metrics.paymentRejected(amount, ex.getCode());
        audit.recordIndependent(AuditEvent.by(actor, AuditAction.PAYMENT_REJECTED, ip,
                "reason=%s %s".formatted(ex.getCode(), subject)));
    }

    private void rejectedConfirmation(UserPrincipal actor, UUID id, BusinessException ex, String ip) {
        var intent = intents.findByIdAndOwnerId(id, actor.id());
        String subject = intent.map(found -> "intent=%s %s".formatted(id, PaymentDetails.of(found).summary()))
                .orElse("intent=" + id);
        rejected(actor, intent.map(PaymentIntent::money).orElse(null), ex, ip, subject);
    }

    private static void requireStatus(PaymentIntent intent, PaymentIntentStatus expected) {
        if (intent.getStatus() != expected) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The payment is " + intent.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " and cannot be confirmed");
        }
    }
}
