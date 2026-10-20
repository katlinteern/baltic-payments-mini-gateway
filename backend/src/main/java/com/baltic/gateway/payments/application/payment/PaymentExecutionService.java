package com.baltic.gateway.payments.application.payment;

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
import com.baltic.gateway.payments.domain.transaction.Transaction;
import com.baltic.gateway.payments.infrastructure.transaction.TransactionRepository;
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
    public record PaymentResult(Transaction transaction, boolean replay) {}

    private final AccountService accounts;
    private final TransferService transfers;
    private final TransactionRepository transactions;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final GatewayMetrics metrics;

    public PaymentExecutionService(AccountService accounts, TransferService transfers,
                                   TransactionRepository transactions, AuditService audit,
                                   PlatformTransactionManager transactionManager, Clock clock, GatewayMetrics metrics) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.transactions = transactions;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.metrics = metrics;
    }

    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        return execute(actor, command, key, ip, () -> {}, booked -> {});
    }

    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                 Runnable beforeBooking, Consumer<Transaction> afterBooking) {
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
                                        Runnable beforeBooking, Consumer<Transaction> afterBooking) {
        try {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        } catch (DataIntegrityViolationException ex) {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        }
    }

    private PaymentResult attempt(UserPrincipal actor, PaymentCommand command, String key, String ip,
                                  Runnable beforeBooking, Consumer<Transaction> afterBooking) {
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

        var replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        accounts.requireOwnedBy(fromIban, actor.id());
        var parties = transfers.lockForTransfer(fromIban, toIban);

        replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        NameCheckResult nameCheck = PayeeNameMatcher.check(details.beneficiaryName(), parties.target().holderName());
        if (nameCheck == NameCheckResult.NO_MATCH && !command.proceedDespiteNameMismatch()) {
            throw new BusinessException(ErrorCode.BENEFICIARY_NAME_MISMATCH,
                    "The beneficiary name does not match the holder of the destination account");
        }

        var payment = transactions.save(new Transaction(parties.source(), parties.target(), actor.id(),
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
