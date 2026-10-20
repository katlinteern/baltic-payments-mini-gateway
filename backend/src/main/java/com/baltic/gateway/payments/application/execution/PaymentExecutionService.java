package com.baltic.gateway.payments.application.execution;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.accounts.application.transfer.TransferService;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.payments.domain.payee.NameCheckResult;
import com.baltic.gateway.payments.domain.payee.PayeeNameMatcher;
import com.baltic.gateway.payments.domain.transaction.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.transaction.PaymentTransactionRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentExecutionService {
    public record PaymentResult(PaymentTransaction transaction, boolean replay) {}

    private static final Set<HttpStatus> AUDITED_REJECTIONS =
            Set.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY);

    private final AccountService accounts;
    private final TransferService transfers;
    private final PaymentTransactionRepository transactions;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final GatewayMetrics metrics;

    public PaymentExecutionService(AccountService accounts, TransferService transfers,
                                   PaymentTransactionRepository transactions, AuditService audit,
                                   PlatformTransactionManager transactionManager, Clock clock, GatewayMetrics metrics) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.transactions = transactions;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.metrics = metrics;
    }

    /** Rule violations worth a fraud/audit trail; plain request validation errors (400) are not recorded. */
    public static boolean isAuditedRejection(BusinessException ex) {
        return AUDITED_REJECTIONS.contains(ex.getStatus());
    }

    public static String normalize(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    /** The accounts and amount of a payment, worded the same way in every audit entry about it. */
    public static String describe(String fromIban, String toIban, Money amount) {
        return "from=%s to=%s amount=%s %s".formatted(normalize(fromIban), normalize(toIban), amount.amount(),
                amount.currency());
    }

    /**
     * Executes the payment in a single database transaction. Rejections are audited in a separate
     * transaction so the record survives the rollback of the payment itself.
     */
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        return execute(actor, command, key, ip, () -> {}, booked -> {});
    }

    /**
     * Like {@link #execute(UserPrincipal, PaymentCommand, String, String)}, with work that must succeed or fail together
     * with the payment. {@code beforeBooking} runs first in the payment's transaction, before any account lock, and may
     * veto the payment by throwing; {@code afterBooking} receives the booked (or replayed) transaction and runs in the
     * same transaction, so no committed payment can lack its follow-up.
     */
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                 Runnable beforeBooking, Consumer<PaymentTransaction> afterBooking) {
        try {
            PaymentResult result = metrics.timePayment(
                    () -> inTransaction(actor, command, key, ip, beforeBooking, afterBooking));
            if (result.replay()) metrics.paymentReplayed(command.amount());
            else metrics.paymentCompleted(command.amount());
            return result;
        } catch (BusinessException ex) {
            metrics.paymentRejected(command.amount(), ex.getCode());
            if (isAuditedRejection(ex)) {
                audit.recordIndependent(AuditEvent.by(actor, AuditAction.PAYMENT_REJECTED, ip, "reason=%s key=%s %s"
                        .formatted(ex.getCode(), key, describe(command.fromIban(), command.toIban(), command.amount()))));
            }
            throw ex;
        }
    }

    private PaymentResult inTransaction(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                        Runnable beforeBooking, Consumer<PaymentTransaction> afterBooking) {
        try {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        } catch (DataIntegrityViolationException ex) {
            // The same key raced on different accounts, which no account lock serialises; the unique index rejected
            // the loser. Its retry finds the winner and answers as a replay or as a key reuse.
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        }
    }

    private PaymentResult attempt(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                  Runnable beforeBooking, Consumer<PaymentTransaction> afterBooking) {
        return transaction.execute(status -> {
            beforeBooking.run();
            PaymentResult result = process(actor, command, key, ip);
            afterBooking.accept(result.transaction());
            return result;
        });
    }

    private PaymentResult process(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        String fromIban = normalize(command.fromIban());
        String toIban = normalize(command.toIban());
        var amount = command.amount();
        if (!amount.isPositive()) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }
        if (fromIban.equals(toIban)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
        }

        // A retry is answered before any lock is taken, so it never waits behind a busy account.
        var replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        // Ownership never changes, so it is checked before any row lock is taken.
        accounts.requireOwnedBy(fromIban, actor.id());
        var parties = transfers.lockForTransfer(fromIban, toIban);

        // The same request may have committed while this one waited for the lock.
        replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        NameCheckResult nameCheck = PayeeNameMatcher.check(command.beneficiaryName(), parties.target().holderName());
        if (nameCheck == NameCheckResult.NO_MATCH && !command.proceedDespiteNameMismatch()) {
            throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                    "The beneficiary name does not match the holder of the destination account");
        }

        var payment = transactions.save(new PaymentTransaction(parties.source(), parties.target(), actor.id(),
                command.beneficiaryName(), nameCheck, amount, command.reference(), key, clock.instant()));
        transfers.transfer(payment.getId(), parties, amount);
        audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_EXECUTED, ip,
                "tx=%s %s nameCheck=%s%s".formatted(payment.getId(), describe(fromIban, toIban, amount), nameCheck,
                        nameCheck == NameCheckResult.NO_MATCH ? " mismatchAcknowledgedByPayer=true" : "")));
        return new PaymentResult(payment, false);
    }

    private Optional<PaymentResult> findReplay(UserPrincipal actor, String key, String fromIban, String toIban,
                                               PaymentCommand command) {
        return transactions.findByCreatedByAndIdempotencyKey(actor.id(), key).map(existing -> {
            if (!existing.getFromIban().equals(fromIban) || !existing.getToIban().equals(toIban)
                    || !existing.money().equals(command.amount())
                    || !Objects.equals(existing.getBeneficiaryName(), command.beneficiaryName())
                    || !Objects.equals(existing.getReference(), command.reference())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSE, "This key was already used for a different payment");
            }
            return new PaymentResult(existing, true);
        });
    }
}
