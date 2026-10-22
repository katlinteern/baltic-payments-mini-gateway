package com.baltic.gateway.payments.application.payment;

import com.baltic.gateway.accounts.application.account.AccountService;
import com.baltic.gateway.accounts.application.transfer.TransferService;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.payments.domain.payee.PayeeNameMatcher;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.transactions.application.recording.TransactionRecordingService;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import com.baltic.gateway.transactions.domain.transaction.Transaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class PaymentExecutionService {
    public record PaymentResult(Transaction transaction, boolean replay) {}

    private final AccountService accounts;
    private final TransferService transfers;
    private final TransactionRecordingService transactionRecorder;
    private final AuditService audit;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final GatewayMetrics metrics;

    public PaymentExecutionService(
            AccountService accounts,
            TransferService transfers,
            TransactionRecordingService transactionRecorder,
            AuditService audit,
            PlatformTransactionManager transactionManager,
            Clock clock,
            GatewayMetrics metrics) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.transactionRecorder = transactionRecorder;
        this.audit = audit;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.metrics = metrics;
    }

    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        return execute(actor, command, key, ip, () -> {}, booked -> {});
    }

    public PaymentResult execute(
            UserPrincipal actor,
            PaymentCommand command,
            String key,
            String ip,
            Runnable beforeBooking,
            Consumer<Transaction> afterBooking) {
        try {
            PaymentResult result =
                    metrics.timePayment(() -> inTransaction(actor, command, key, ip, beforeBooking, afterBooking));
            if (result.replay()) metrics.paymentReplayed(command.details().amount());
            else metrics.paymentCompleted(command.details().amount());
            return result;
        } catch (BusinessException ex) {
            metrics.paymentRejected(command.details().amount(), ex.getCode());
            if (ex.getCode().isRuleViolation()) {
                audit.recordIndependent(AuditEvent.by(
                        actor,
                        AuditAction.PAYMENT_REJECTED,
                        ip,
                        "reason=%s key=%s %s"
                                .formatted(ex.getCode(), key, command.details().summary())));
            }
            throw ex;
        }
    }

    private PaymentResult inTransaction(
            UserPrincipal actor,
            PaymentCommand command,
            String key,
            String ip,
            Runnable beforeBooking,
            Consumer<Transaction> afterBooking) {
        try {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        } catch (DataIntegrityViolationException ex) {
            return attempt(actor, command, key, ip, beforeBooking, afterBooking);
        }
    }

    private PaymentResult attempt(
            UserPrincipal actor,
            PaymentCommand command,
            String key,
            String ip,
            Runnable beforeBooking,
            Consumer<Transaction> afterBooking) {
        return transactionTemplate.execute(status -> {
            beforeBooking.run();
            PaymentResult result = process(actor, command, key, ip);
            afterBooking.accept(result.transaction());
            return result;
        });
    }

    private PaymentResult process(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        var details = command.details().validated();
        String fromIban = details.fromIban();
        String toIban = details.toIban();
        var amount = details.amount();

        var replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        accounts.requireOwnedBy(fromIban, actor.id());
        var parties = transfers.lockForTransfer(fromIban, toIban);

        replay = findReplay(actor, key, fromIban, toIban, command);
        if (replay.isPresent()) return replay.get();

        NameCheckResult nameCheck = PayeeNameMatcher.check(
                details.beneficiaryName(), parties.target().holderName());
        if (nameCheck == NameCheckResult.NO_MATCH && !command.proceedDespiteNameMismatch()) {
            throw new BusinessException(
                    ErrorCode.BENEFICIARY_NAME_MISMATCH,
                    "The beneficiary name does not match the holder of the destination account");
        }

        var booked = transactionRecorder.record(new Transaction(
                parties.source(),
                parties.target(),
                actor.id(),
                details.beneficiaryName(),
                nameCheck,
                amount,
                details.reference(),
                key,
                clock.instant()));
        transfers.transfer(booked.getId(), parties, amount);
        audit.record(AuditEvent.by(
                actor,
                AuditAction.PAYMENT_EXECUTED,
                ip,
                "tx=%s %s nameCheck=%s%s"
                        .formatted(
                                booked.getId(),
                                details.summary(),
                                nameCheck,
                                nameCheck == NameCheckResult.NO_MATCH ? " mismatchAcknowledgedByPayer=true" : "")));
        return new PaymentResult(booked, false);
    }

    private Optional<PaymentResult> findReplay(
            UserPrincipal actor, String key, String fromIban, String toIban, PaymentCommand command) {
        var details = command.details();
        return transactionRecorder
                .findByCreatedByAndIdempotencyKey(actor.id(), key)
                .map(existing -> {
                    if (!existing.getFromIban().equals(fromIban)
                            || !existing.getToIban().equals(toIban)
                            || !existing.money().equals(details.amount())
                            || !Objects.equals(existing.getBeneficiaryName(), details.beneficiaryName())
                            || !Objects.equals(existing.getReference(), details.reference())) {
                        throw new BusinessException(
                                ErrorCode.IDEMPOTENCY_KEY_REUSE, "This key was already used for a different payment");
                    }
                    return new PaymentResult(existing, true);
                });
    }
}
