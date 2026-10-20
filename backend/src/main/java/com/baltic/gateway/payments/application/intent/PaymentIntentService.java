package com.baltic.gateway.payments.application.intent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.accounts.application.ledger.LedgerService;
import com.baltic.gateway.accounts.application.transfer.TransferService;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import com.baltic.gateway.payments.application.execution.PaymentCommand;
import com.baltic.gateway.payments.application.execution.PaymentExecutionService;
import com.baltic.gateway.payments.application.payee.PayeeVerificationService;
import com.baltic.gateway.payments.domain.intent.IntentStatus;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.infrastructure.intent.PaymentIntentRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The authentication outcome is read when the client asks for the intent, not pushed: whoever polls sees an approved
 * intent executed. The execution key derives from the intent id, so concurrent polls cannot pay twice.
 */
@Service
public class PaymentIntentService {
    private final PaymentIntentRepository intents;
    private final AccountService accounts;
    private final TransferService transfers;
    private final LedgerService ledger;
    private final PayeeVerificationService payeeVerification;
    private final PaymentExecutionService execution;
    private final AuditService audit;
    private final GatewayMetrics metrics;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration intentTtl;
    private final Duration authenticationTtl;

    public PaymentIntentService(PaymentIntentRepository intents, AccountService accounts, TransferService transfers,
                                LedgerService ledger, PayeeVerificationService payeeVerification,
                                PaymentExecutionService execution,
                                AuditService audit, GatewayMetrics metrics,
                                ObjectProvider<StrongAuthenticator> authenticator,
                                PlatformTransactionManager transactionManager, Clock clock,
                                @Value("${app.payments.intent-ttl}") Duration intentTtl,
                                @Value("${app.payments.authentication-ttl}") Duration authenticationTtl) {
        this.intents = intents;
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.payeeVerification = payeeVerification;
        this.execution = execution;
        this.audit = audit;
        this.metrics = metrics;
        this.authenticator = authenticator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.intentTtl = intentTtl;
        this.authenticationTtl = authenticationTtl;
    }

    static String executionKey(UUID intentId) {
        return "intent-" + intentId;
    }

    public IntentView create(UserPrincipal actor, PaymentIntentCommand command, String ip) {
        Money amount = command.amount();
        try {
            String fromIban = PaymentExecutionService.normalize(command.fromIban());
            String toIban = PaymentExecutionService.normalize(command.toIban());
            if (!amount.isPositive()) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
            }
            if (fromIban.equals(toIban)) {
                throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
            }
            return transaction.execute(status -> {
                accounts.requireOwnedBy(fromIban, actor.id());
                transfers.checkTransferable(fromIban, toIban, amount);
                var verification = payeeVerification.verify(toIban, command.beneficiaryName());
                Instant now = clock.instant();
                var intent = intents.save(new PaymentIntent(actor.id(), fromIban, toIban, command.beneficiaryName(),
                        amount, command.reference(), verification.result(), verification.registeredName(), now,
                        now.plus(intentTtl)));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_INTENT_CREATED, ip,
                        "intent=%s %s nameCheck=%s".formatted(intent.getId(),
                                PaymentExecutionService.describe(fromIban, toIban, amount), verification.result())));
                return new IntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejected(actor, amount, ex, ip,
                    PaymentExecutionService.describe(command.fromIban(), command.toIban(), amount));
            throw ex;
        }
    }

    public IntentView startConfirmation(UserPrincipal actor, UUID id, boolean acknowledgeNameMismatch, String ip) {
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (strongAuthenticator == null) {
            throw new BusinessException(ErrorCode.STRONG_AUTHENTICATION_UNAVAILABLE,
                    "No authentication provider is configured, so payments cannot be confirmed");
        }
        try {
            return transaction.execute(status -> {
                PaymentIntent intent = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElseThrow(this::notFound);
                Instant now = clock.instant();
                intent.expireIfDue(now);
                if (intent.getStatus() == IntentStatus.AWAITING_AUTHENTICATION) return new IntentView(intent, null);
                requireStatus(intent, IntentStatus.READY);
                if (intent.getNameCheck() == NameCheckResult.NO_MATCH && !acknowledgeNameMismatch) {
                    throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                            "The beneficiary name does not match the holder of the destination account");
                }
                var challenge = strongAuthenticator.start(now);
                intent.startAuthentication(acknowledgeNameMismatch, challenge.verificationCode(), now,
                        now.plus(authenticationTtl));
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_STARTED, ip,
                        "intent=%s".formatted(intent.getId())));
                return new IntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejectedConfirmation(actor, id, ex, ip);
            throw ex;
        }
    }

    /** Reading an approved intent executes the payment; reading an overdue one ends it. */
    public IntentView get(UserPrincipal actor, UUID id, String ip) {
        PaymentIntent intent = intents.findByIdAndOwnerId(id, actor.id()).orElseThrow(this::notFound);
        if (intent.isDue(clock.instant())) intent = expire(actor, id);
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (intent.getStatus() != IntentStatus.AWAITING_AUTHENTICATION || strongAuthenticator == null) {
            return view(intent);
        }
        var challenge = new StrongAuthenticator.Challenge(intent.getVerificationCode(), intent.getAuthenticationStartedAt());
        return switch (strongAuthenticator.outcome(challenge, clock.instant())) {
            case PENDING -> view(intent);
            case REJECTED -> view(end(actor, id, intent, "AUTHENTICATION_REJECTED",
                    "The payment was not approved in the authentication app", ip));
            case APPROVED -> execute(actor, intent, ip);
        };
    }

    public IntentView cancel(UserPrincipal actor, UUID id, String ip) {
        return transaction.execute(status -> {
            PaymentIntent intent = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElseThrow(this::notFound);
            intent.expireIfDue(clock.instant());
            if (intent.getStatus() == IntentStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT, "The payment has already been executed");
            }
            if (intent.getStatus().isOpen()) {
                intent.cancel(clock.instant());
                audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_INTENT_CANCELLED, ip,
                        "intent=%s".formatted(intent.getId())));
            }
            return new IntentView(intent, null);
        });
    }

    /** Under the row lock, so a payment executing right now is not undone. */
    private PaymentIntent expire(UserPrincipal actor, UUID id) {
        return transaction.execute(status -> {
            PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElseThrow(this::notFound);
            current.expireIfDue(clock.instant());
            return current;
        });
    }

    private IntentView execute(UserPrincipal actor, PaymentIntent intent, String ip) {
        UUID id = intent.getId();
        var command = new PaymentCommand(intent.getFromIban(), intent.getToIban(), intent.getBeneficiaryName(),
                intent.money(), intent.getReference(), intent.isAcknowledgedNameMismatch());
        var locked = new AtomicReference<PaymentIntent>();
        try {
            execution.execute(actor, command, executionKey(id), ip,
                    () -> locked.set(lockAwaiting(actor, id)),
                    booked -> locked.get().complete(booked.getId(), clock.instant()));
            return view(locked.get());
        } catch (NoLongerAwaiting ex) {
            return view(intents.findByIdAndOwnerId(id, actor.id()).orElseThrow(this::notFound));
        } catch (BusinessException ex) {
            return view(end(actor, id, intent, ex.getCode().name(), ex.getMessage(), ip));
        }
    }

    private PaymentIntent lockAwaiting(UserPrincipal actor, UUID id) {
        PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElseThrow(this::notFound);
        if (current.getStatus() != IntentStatus.AWAITING_AUTHENTICATION || current.isDue(clock.instant())) {
            throw new NoLongerAwaiting();
        }
        return current;
    }

    private static final class NoLongerAwaiting extends RuntimeException {
        NoLongerAwaiting() {
            super(null, null, false, false);
        }
    }

    private PaymentIntent end(UserPrincipal actor, UUID id, PaymentIntent fallback, String code, String message, String ip) {
        return transaction.execute(status -> {
            PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElse(fallback);
            if (current.getStatus() == IntentStatus.AWAITING_AUTHENTICATION) {
                current.fail(code, message, clock.instant());
                if ("AUTHENTICATION_REJECTED".equals(code)) {
                    audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_REJECTED, ip,
                            "intent=%s".formatted(id)));
                }
            }
            return current;
        });
    }

    private IntentView view(PaymentIntent intent) {
        if (intent.getStatus() != IntentStatus.COMPLETED || intent.getTransactionId() == null) {
            return new IntentView(intent, null);
        }
        BigDecimal balanceAfter = ledger.postingsOf(List.of(intent.getTransactionId())).stream()
                .filter(entry -> entry.getDirection() == EntryDirection.DEBIT)
                .map(entry -> entry.getBalanceAfter())
                .findFirst().orElse(null);
        return new IntentView(intent, balanceAfter == null ? null : Money.of(balanceAfter, intent.money().currency()).amount());
    }

    private void rejected(UserPrincipal actor, Money amount, BusinessException ex, String ip, String subject) {
        if (!PaymentExecutionService.isAuditedRejection(ex)) return;
        if (amount != null) metrics.paymentRejected(amount, ex.getCode());
        audit.recordIndependent(AuditEvent.by(actor, AuditAction.PAYMENT_REJECTED, ip,
                "reason=%s %s".formatted(ex.getCode(), subject)));
    }

    private void rejectedConfirmation(UserPrincipal actor, UUID id, BusinessException ex, String ip) {
        var intent = intents.findByIdAndOwnerId(id, actor.id());
        String subject = intent.map(found -> "intent=%s %s".formatted(id,
                        PaymentExecutionService.describe(found.getFromIban(), found.getToIban(), found.money())))
                .orElse("intent=" + id);
        rejected(actor, intent.map(PaymentIntent::money).orElse(null), ex, ip, subject);
    }

    private static void requireStatus(PaymentIntent intent, IntentStatus expected) {
        if (intent.getStatus() != expected) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The payment is " + intent.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " and cannot be confirmed");
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Unknown payment");
    }
}
