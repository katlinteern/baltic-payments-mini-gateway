package com.baltic.gateway.payments.application.execution;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
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
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.validation.Iban;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentExecutionService {
    public record PaymentResult(PaymentTransaction transaction, boolean replay) {}

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

    /** Rejections are audited in a separate transaction, so the record survives the rollback of the payment. */
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        return execute(actor, command, key, ip, () -> {}, booked -> {});
    }

    /**
     * beforeBooking runs first in the payment's transaction, before any account lock, and may veto the payment by throwing;
     * afterBooking receives the booked (or replayed) transaction in the same transaction, so no payment commits without it.
     */
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                 Runnable beforeBooking, Consumer<PaymentTransaction> afterBooking) {
        try {
            PaymentResult result = metrics.timePayment(
                    () -> inTransaction(actor, command, key, ip, beforeBooking, afterBooking));
            if (result.replay()) metrics.paymentReplayed(command.details().amount());
            else metrics.paymentCompleted(command.details().amount());
            return result;
        } catch (BusinessException ex) {
            metrics.paymentRejected(command.details().amount(), ex.getCode());
            if (ex.getCode().isRuleViolation()) {
                audit.recordIndependent(AuditEvent.by(actor, AuditAction.PAYMENT_REJECTED, ip, "reason=%s key=%s %s"
                        .formatted(ex.getCode(), key, command.details().summary())));
            }
            throw ex;
        }
    }

    private PaymentResult inTransaction(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                        Runnable beforeBooking, Consumer<PaymentTransaction> afterBooking) {
        try {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        } catch (DataIntegrityViolationException ex) {
            // The same key raced on different accounts, which no account lock serialises; the unique index rejected the loser,
            // and its retry finds the winner and answers as a replay or a key reuse.
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
        var details = command.details();
        String fromIban = Iban.normalize(details.fromIban());
        String toIban = Iban.normalize(details.toIban());
        var amount = details.amount();
        if (!amount.isPositive()) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }
        if (fromIban.equals(toIban)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER, "Source and destination accounts must differ");
        }

        // A retry is answered before any lock, so it never waits behind a busy account.
        var replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        // Ownership never changes, so it is checked before any row lock.
        accounts.requireOwnedBy(fromIban, actor.id());
        var parties = transfers.lockForTransfer(fromIban, toIban);

        // The same request may have committed while this one waited for the lock.
        replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        NameCheckResult nameCheck = PayeeNameMatcher.check(details.beneficiaryName(), parties.target().holderName());
        if (nameCheck == NameCheckResult.NO_MATCH && !command.proceedDespiteNameMismatch()) {
            throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                    "The beneficiary name does not match the holder of the destination account");
        }

        var payment = transactions.save(new PaymentTransaction(parties.source(), parties.target(), actor.id(),
                details.beneficiaryName(), nameCheck, amount, details.reference(), key, clock.instant()));
        transfers.transfer(payment.getId(), parties, amount);
        audit.record(AuditEvent.by(actor, AuditAction.PAYMENT_EXECUTED, ip,
                "tx=%s %s nameCheck=%s%s".formatted(payment.getId(), details.summary(), nameCheck,
                        nameCheck == NameCheckResult.NO_MATCH ? " mismatchAcknowledgedByPayer=true" : "")));
        return new PaymentResult(payment, false);
    }

    private Optional<PaymentResult> findReplay(UserPrincipal actor, String key, String fromIban, String toIban,
                                               PaymentCommand command) {
        var details = command.details();
        return transactions.findByCreatedByAndIdempotencyKey(actor.id(), key).map(existing -> {
            if (!existing.getFromIban().equals(fromIban) || !existing.getToIban().equals(toIban)
                    || !existing.money().equals(details.amount())
                    || !Objects.equals(existing.getBeneficiaryName(), details.beneficiaryName())
                    || !Objects.equals(existing.getReference(), details.reference())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSE, "This key was already used for a different payment");
            }
            return new PaymentResult(existing, true);
        });
    }
}
