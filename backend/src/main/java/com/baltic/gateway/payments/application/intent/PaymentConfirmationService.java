package com.baltic.gateway.payments.application.intent;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.baltic.gateway.accounts.application.ledger.LedgerService;
import com.baltic.gateway.accounts.application.ledger.LedgerEntryView;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import com.baltic.gateway.payments.application.payment.PaymentCommand;
import com.baltic.gateway.payments.application.payment.PaymentDetails;
import com.baltic.gateway.payments.application.payment.PaymentExecutionService;
import com.baltic.gateway.payments.domain.intent.PaymentIntentStatus;
import com.baltic.gateway.payments.domain.intent.PaymentIntent;
import com.baltic.gateway.payments.infrastructure.intent.PaymentIntentRepository;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentConfirmationService {
    static final String AUTHENTICATION_REJECTED = "AUTHENTICATION_REJECTED";

    private final PaymentIntentRepository intents;
    private final PaymentExecutionService execution;
    private final LedgerService ledger;
    private final AuditService audit;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentConfirmationService(PaymentIntentRepository intents, PaymentExecutionService execution,
                                      LedgerService ledger, AuditService audit,
                                      ObjectProvider<StrongAuthenticator> authenticator,
                                      PlatformTransactionManager transactionManager, Clock clock) {
        this.intents = intents;
        this.execution = execution;
        this.ledger = ledger;
        this.audit = audit;
        this.authenticator = authenticator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    private static String executionKey(UUID intentId) {
        return "intent-" + intentId;
    }

    public PaymentIntentView checkConfirmation(UserPrincipal actor, UUID id, String ip) {
        PaymentIntent intent = intents.findByIdAndOwnerId(id, actor.id()).orElseThrow(PaymentIntentService::notFound);
        if (intent.isDue(clock.instant())) intent = expire(actor, id);
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (intent.getStatus() != PaymentIntentStatus.AWAITING_AUTHENTICATION || strongAuthenticator == null) {
            return view(intent);
        }
        var challenge = new StrongAuthenticator.Challenge(intent.getVerificationCode(), intent.getAuthenticationStartedAt());
        return switch (strongAuthenticator.outcome(challenge, clock.instant())) {
            case PENDING -> view(intent);
            case REJECTED -> view(end(actor, id, intent, AUTHENTICATION_REJECTED,
                    "The payment was not approved in the authentication app", ip));
            case APPROVED -> execute(actor, intent, ip);
        };
    }

    private PaymentIntent expire(UserPrincipal actor, UUID id) {
        return transaction.execute(status -> {
            PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id())
                    .orElseThrow(PaymentIntentService::notFound);
            current.expireIfDue(clock.instant());
            return current;
        });
    }

    private PaymentIntentView execute(UserPrincipal actor, PaymentIntent intent, String ip) {
        UUID id = intent.getId();
        var command = new PaymentCommand(PaymentDetails.of(intent), intent.isAcknowledgedNameMismatch());
        var locked = new AtomicReference<PaymentIntent>();
        try {
            execution.execute(actor, command, executionKey(id), ip,
                    () -> locked.set(lockAwaiting(actor, id)),
                    booked -> locked.get().complete(booked.getId(), clock.instant()));
            return view(locked.get());
        } catch (NoLongerAwaiting ex) {
            return view(intents.findByIdAndOwnerId(id, actor.id()).orElseThrow(PaymentIntentService::notFound));
        } catch (BusinessException ex) {
            return view(end(actor, id, intent, ex.getCode().name(), ex.getMessage(), ip));
        }
    }

    private PaymentIntent lockAwaiting(UserPrincipal actor, UUID id) {
        PaymentIntent current = intents.findByIdAndOwnerIdForUpdate(id, actor.id())
                .orElseThrow(PaymentIntentService::notFound);
        if (current.getStatus() != PaymentIntentStatus.AWAITING_AUTHENTICATION || current.isDue(clock.instant())) {
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
            if (current.getStatus() == PaymentIntentStatus.AWAITING_AUTHENTICATION) {
                current.fail(code, message, clock.instant());
                if (AUTHENTICATION_REJECTED.equals(code)) {
                    audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_REJECTED, ip,
                            "intent=%s".formatted(id)));
                }
            }
            return current;
        });
    }

    private PaymentIntentView view(PaymentIntent intent) {
        if (intent.getStatus() != PaymentIntentStatus.COMPLETED || intent.getTransactionId() == null) {
            return new PaymentIntentView(intent, null);
        }
        BigDecimal balanceAfter = ledger.entriesOf(List.of(intent.getTransactionId())).stream()
                .filter(entry -> entry.direction() == EntryDirection.DEBIT)
                .map(LedgerEntryView::balanceAfter)
                .findFirst().orElse(null);
        return new PaymentIntentView(intent,
                balanceAfter == null ? null : Money.of(balanceAfter, intent.money().currency()).amount());
    }
}
