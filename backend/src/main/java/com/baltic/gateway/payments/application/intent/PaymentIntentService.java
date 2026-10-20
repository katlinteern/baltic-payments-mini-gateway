package com.baltic.gateway.payments.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.baltic.gateway.accounts.application.AccountService;
import com.baltic.gateway.accounts.domain.EntryDirection;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.identity.application.StrongAuthenticator;
import com.baltic.gateway.payments.domain.IntentStatus;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PaymentIntent;
import com.baltic.gateway.payments.infrastructure.PaymentIntentRepository;
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
 * Payments the way a bank does them: the payer prepares a payment (the server checks the accounts and the beneficiary
 * name), reviews it, confirms it with strong authentication, and only then does money move.
 *
 * <p>The authentication outcome is read when the client asks for the intent's state, not pushed: whoever polls sees an
 * approved intent executed. Execution uses a key derived from the intent id, so concurrent polls cannot pay twice and a
 * poll that failed halfway simply repeats.
 */
@Service
public class PaymentIntentService {
    private final PaymentIntentRepository intents;
    private final AccountService accounts;
    private final PayeeVerificationService payeeVerification;
    private final PaymentService payments;
    private final AuditService audit;
    private final GatewayMetrics metrics;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration intentTtl;
    private final Duration authenticationTtl;

    public PaymentIntentService(PaymentIntentRepository intents, AccountService accounts,
                                PayeeVerificationService payeeVerification, PaymentService payments,
                                AuditService audit, GatewayMetrics metrics,
                                ObjectProvider<StrongAuthenticator> authenticator,
                                PlatformTransactionManager transactionManager, Clock clock,
                                @Value("${app.payments.intent-ttl}") Duration intentTtl,
                                @Value("${app.payments.authentication-ttl}") Duration authenticationTtl) {
        this.intents = intents;
        this.accounts = accounts;
        this.payeeVerification = payeeVerification;
        this.payments = payments;
        this.audit = audit;
        this.metrics = metrics;
        this.authenticator = authenticator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.intentTtl = intentTtl;
        this.authenticationTtl = authenticationTtl;
    }

    /** Key under which the payment of an intent is executed; the same intent can therefore be paid only once. */
    static String executionKey(UUID intentId) {
        return "intent-" + intentId;
    }

    public IntentView create(UserPrincipal actor, PaymentIntentCommand command, String ip) {
        Money amount = command.amount();
        try {
            String fromIban = PaymentService.normalize(command.fromIban());
            String toIban = PaymentService.normalize(command.toIban());
            if (!amount.isPositive()) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
            }
            if (fromIban.equals(toIban)) {
                throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
            }
            return transaction.execute(status -> {
                accounts.requireOwnedBy(fromIban, actor.id());
                accounts.checkTransferable(fromIban, toIban, amount);
                var verification = payeeVerification.verify(toIban, command.beneficiaryName());
                Instant now = clock.instant();
                var intent = intents.save(new PaymentIntent(actor.id(), fromIban, toIban, command.beneficiaryName(),
                        amount, command.reference(), verification.result(), verification.registeredName(), now,
                        now.plus(intentTtl)));
                audit.record("PAYMENT_INTENT_CREATED", actor.id(), actor.username(), ip,
                        "intent=%s from=%s to=%s amount=%s %s nameCheck=%s".formatted(intent.getId(), fromIban, toIban,
                                amount.amount(), amount.currency(), verification.result()));
                return new IntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejected(actor, amount, ex, ip);
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
                audit.record("PAYMENT_AUTHENTICATION_STARTED", actor.id(), actor.username(), ip,
                        "intent=%s".formatted(intent.getId()));
                return new IntentView(intent, null);
            });
        } catch (BusinessException ex) {
            rejected(actor, intentAmount(actor, id), ex, ip);
            throw ex;
        }
    }

    /** The current state; an approved authentication is executed here, an overdue intent is ended. */
    public IntentView get(UserPrincipal actor, UUID id, String ip) {
        PaymentIntent intent = transaction.execute(status -> {
            PaymentIntent found = intents.findByIdAndOwnerId(id, actor.id()).orElseThrow(this::notFound);
            found.expireIfDue(clock.instant());
            return found;
        });
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
                audit.record("PAYMENT_INTENT_CANCELLED", actor.id(), actor.username(), ip,
                        "intent=%s".formatted(intent.getId()));
            }
            return new IntentView(intent, null);
        });
    }

    private IntentView execute(UserPrincipal actor, PaymentIntent intent, String ip) {
        var command = new PaymentCommand(intent.getFromIban(), intent.getToIban(), intent.getBeneficiaryName(),
                intent.money(), intent.getReference(), intent.isAcknowledgedNameMismatch());
        try {
            var result = payments.execute(actor, command, executionKey(intent.getId()), ip);
            return view(transaction.execute(status -> {
                PaymentIntent current = intents.findByIdAndOwnerId(intent.getId(), actor.id()).orElseThrow(this::notFound);
                current.complete(result.transaction().getId(), clock.instant());
                return current;
            }));
        } catch (BusinessException ex) {
            return view(end(actor, intent.getId(), intent, ex.getCode().name(), ex.getMessage(), ip));
        }
    }

    /** Marks the intent failed unless something else (a cancellation, an expiry, another poll) ended it first. */
    private PaymentIntent end(UserPrincipal actor, UUID id, PaymentIntent fallback, String code, String message, String ip) {
        return transaction.execute(status -> {
            PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id()).orElse(fallback);
            if (current.getStatus() == IntentStatus.AWAITING_AUTHENTICATION) {
                current.fail(code, message, clock.instant());
                if ("AUTHENTICATION_REJECTED".equals(code)) {
                    audit.record("PAYMENT_AUTHENTICATION_REJECTED", actor.id(), actor.username(), ip,
                            "intent=%s".formatted(id));
                }
            }
            return current;
        });
    }

    private IntentView view(PaymentIntent intent) {
        if (intent.getStatus() != IntentStatus.COMPLETED || intent.getTransactionId() == null) {
            return new IntentView(intent, null);
        }
        BigDecimal balanceAfter = accounts.postingsOf(List.of(intent.getTransactionId())).stream()
                .filter(entry -> entry.getDirection() == EntryDirection.DEBIT)
                .map(entry -> entry.getBalanceAfter())
                .findFirst().orElse(null);
        return new IntentView(intent, balanceAfter == null ? null : Money.of(balanceAfter, intent.money().currency()).amount());
    }

    private void rejected(UserPrincipal actor, Money amount, BusinessException ex, String ip) {
        if (!PaymentService.isAuditedRejection(ex)) return;
        if (amount != null) metrics.paymentRejected(amount, ex.getCode());
        audit.recordIndependent("PAYMENT_REJECTED", actor.id(), actor.username(), ip, ex.getTitle());
    }

    private Money intentAmount(UserPrincipal actor, UUID id) {
        return intents.findByIdAndOwnerId(id, actor.id()).map(PaymentIntent::money).orElse(null);
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
