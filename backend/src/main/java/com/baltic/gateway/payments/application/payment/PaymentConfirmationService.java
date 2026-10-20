package com.baltic.gateway.payments.application.payment;

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
import com.baltic.gateway.payments.domain.payment.Payment;
import com.baltic.gateway.payments.domain.payment.PaymentStatus;
import com.baltic.gateway.payments.infrastructure.payment.PaymentRepository;
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

    private final PaymentRepository payments;
    private final PaymentExecutionService execution;
    private final LedgerService ledger;
    private final AuditService audit;
    private final ObjectProvider<StrongAuthenticator> authenticator;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentConfirmationService(PaymentRepository payments, PaymentExecutionService execution,
                                      LedgerService ledger, AuditService audit,
                                      ObjectProvider<StrongAuthenticator> authenticator,
                                      PlatformTransactionManager transactionManager, Clock clock) {
        this.payments = payments;
        this.execution = execution;
        this.ledger = ledger;
        this.audit = audit;
        this.authenticator = authenticator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    private static String executionKey(UUID paymentId) {
        return "payment-" + paymentId;
    }

    public PaymentView checkConfirmation(UserPrincipal actor, UUID id, String ip) {
        Payment payment = payments.findByIdAndOwnerId(id, actor.id()).orElseThrow(PaymentService::notFound);
        if (payment.isDue(clock.instant())) payment = expire(actor, id);
        StrongAuthenticator strongAuthenticator = authenticator.getIfAvailable();
        if (payment.getStatus() != PaymentStatus.AWAITING_AUTHENTICATION || strongAuthenticator == null) {
            return view(payment);
        }
        var challenge = new StrongAuthenticator.Challenge(payment.getVerificationCode(),
                payment.getAuthenticationStartedAt());
        return switch (strongAuthenticator.outcome(challenge, clock.instant())) {
            case PENDING -> view(payment);
            case REJECTED -> view(end(actor, id, payment, AUTHENTICATION_REJECTED,
                    "The payment was not approved in the authentication app", ip));
            case APPROVED -> execute(actor, payment, ip);
        };
    }

    private Payment expire(UserPrincipal actor, UUID id) {
        return transaction.execute(status -> {
            Payment current = payments.findByIdAndOwnerIdForUpdate(id, actor.id())
                    .orElseThrow(PaymentService::notFound);
            current.expireIfDue(clock.instant());
            return current;
        });
    }

    private PaymentView execute(UserPrincipal actor, Payment payment, String ip) {
        UUID id = payment.getId();
        var command = new PaymentCommand(PaymentDetails.of(payment), payment.isAcknowledgedNameMismatch());
        var locked = new AtomicReference<Payment>();
        try {
            execution.execute(actor, command, executionKey(id), ip,
                    () -> locked.set(lockAwaiting(actor, id)),
                    booked -> locked.get().complete(booked.getId(), clock.instant()));
            return view(locked.get());
        } catch (NoLongerAwaiting ex) {
            return view(payments.findByIdAndOwnerId(id, actor.id()).orElseThrow(PaymentService::notFound));
        } catch (BusinessException ex) {
            return view(end(actor, id, payment, ex.getCode().name(), ex.getMessage(), ip));
        }
    }

    private Payment lockAwaiting(UserPrincipal actor, UUID id) {
        Payment current = payments.findByIdAndOwnerIdForUpdate(id, actor.id())
                .orElseThrow(PaymentService::notFound);
        if (current.getStatus() != PaymentStatus.AWAITING_AUTHENTICATION || current.isDue(clock.instant())) {
            throw new NoLongerAwaiting();
        }
        return current;
    }

    private static final class NoLongerAwaiting extends RuntimeException {
        NoLongerAwaiting() {
            super(null, null, false, false);
        }
    }

    private Payment end(UserPrincipal actor, UUID id, Payment fallback, String code, String message, String ip) {
        return transaction.execute(status -> {
            Payment current = payments.findByIdAndOwnerIdForUpdate(id, actor.id()).orElse(fallback);
            if (current.getStatus() == PaymentStatus.AWAITING_AUTHENTICATION) {
                current.fail(code, message, clock.instant());
                if (AUTHENTICATION_REJECTED.equals(code)) {
                    audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_AUTHENTICATION_REJECTED, ip,
                            "payment=%s".formatted(id)));
                }
            }
            return current;
        });
    }

    private PaymentView view(Payment payment) {
        if (payment.getStatus() != PaymentStatus.COMPLETED || payment.getTransactionId() == null) {
            return new PaymentView(payment, null);
        }
        BigDecimal balanceAfter = ledger.entriesOf(List.of(payment.getTransactionId())).stream()
                .filter(entry -> entry.direction() == EntryDirection.DEBIT)
                .map(LedgerEntryView::balanceAfter)
                .findFirst().orElse(null);
        return new PaymentView(payment,
                balanceAfter == null ? null : Money.of(balanceAfter, payment.money().currency()).amount());
    }
}
