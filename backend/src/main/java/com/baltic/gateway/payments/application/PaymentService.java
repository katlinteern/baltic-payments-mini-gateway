package com.baltic.gateway.payments.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.baltic.gateway.accounts.application.AccountService;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PayeeNameMatcher;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.TransactionRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    public record PaymentResult(PaymentTransaction transaction, boolean replay) {}

    // Rule violations worth a fraud/audit trail; plain request validation errors (400) are not recorded.
    private static final Set<HttpStatus> AUDITED_REJECTIONS =
            Set.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY);

    private final AccountService accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final GatewayMetrics metrics;

    public PaymentService(AccountService accounts, TransactionRepository transactions, AuditService audit,
                          PlatformTransactionManager transactionManager, Clock clock, GatewayMetrics metrics) {
        this.accounts = accounts;
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
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    /**
     * Executes the payment in a single database transaction. Rejections are audited in a separate
     * transaction so the record survives the rollback of the payment itself.
     */
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        try {
            PaymentResult result = metrics.timePayment(() -> inTransaction(actor, command, key, ip));
            if (result.replay()) metrics.paymentReplayed(command.amount());
            else metrics.paymentCompleted(command.amount());
            return result;
        } catch (BusinessException ex) {
            metrics.paymentRejected(command.amount(), ex.getCode());
            if (isAuditedRejection(ex)) {
                audit.recordIndependent("PAYMENT_REJECTED", actor.id(), actor.username(), ip, ex.getTitle());
            }
            throw ex;
        }
    }

    private PaymentResult inTransaction(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        try {
            return transaction.execute(status -> process(actor, command, key, ip));
        } catch (DataIntegrityViolationException ex) {
            // The same key raced on different accounts, which no account lock serialises; the unique index rejected
            // the loser. Its retry finds the winner and answers as a replay or as a key reuse.
            return transaction.execute(status -> process(actor, command, key, ip));
        }
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
        var parties = accounts.lockForTransfer(fromIban, toIban);

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
        accounts.transfer(payment.getId(), fromIban, toIban, amount);
        audit.record("PAYMENT_EXECUTED", actor.id(), actor.username(), ip,
                "tx=%s from=%s to=%s amount=%s %s nameCheck=%s%s".formatted(payment.getId(), fromIban, toIban,
                        amount.amount(), amount.currency(), nameCheck,
                        nameCheck == NameCheckResult.NO_MATCH ? " mismatchAcknowledgedByPayer=true" : ""));
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
