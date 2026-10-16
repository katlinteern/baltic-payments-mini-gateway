package lt.baltic.gateway.payments.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lt.baltic.gateway.accounts.application.AccountService;
import lt.baltic.gateway.audit.application.AuditService;
import lt.baltic.gateway.payments.domain.NameCheckResult;
import lt.baltic.gateway.payments.domain.PayeeNameMatcher;
import lt.baltic.gateway.payments.domain.PaymentTransaction;
import lt.baltic.gateway.payments.infrastructure.TransactionRepository;
import lt.baltic.gateway.platform.security.UserPrincipal;
import lt.baltic.gateway.platform.web.BusinessException;
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

    public PaymentService(AccountService accounts, TransactionRepository transactions, AuditService audit,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
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
            return transaction.execute(status -> process(actor, command, key, ip));
        } catch (BusinessException ex) {
            if (AUDITED_REJECTIONS.contains(ex.getStatus())) {
                audit.recordIndependent("PAYMENT_REJECTED", actor.id(), actor.username(), ip, ex.getTitle());
            }
            throw ex;
        }
    }

    private PaymentResult process(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        String fromIban = normalize(command.fromIban());
        String toIban = normalize(command.toIban());
        var amount = command.amount();
        if (!amount.isPositive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid amount", "Payment amount must be greater than zero");
        }
        if (fromIban.equals(toIban)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid transfer", "Source and destination accounts must differ");
        }

        var locked = accounts.lockForUpdate(List.of(fromIban, toIban));
        var source = locked.get(fromIban);
        var target = locked.get(toIban);

        var prior = transactions.findByCreatedByAndIdempotencyKey(actor.id(), key);
        if (prior.isPresent()) {
            PaymentTransaction existing = prior.get();
            if (!existing.getFromIban().equals(fromIban) || !existing.getToIban().equals(toIban)
                    || !existing.money().equals(amount)
                    || !Objects.equals(existing.getBeneficiaryName(), command.beneficiaryName())
                    || !Objects.equals(existing.getReference(), command.reference())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse", "This key was already used for a different payment");
            }
            return new PaymentResult(existing, true);
        }

        if (!source.getOwnerId().equals(actor.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden", "Source account is not yours");
        }
        NameCheckResult nameCheck = PayeeNameMatcher.check(command.beneficiaryName(), target.getHolderName());
        if (nameCheck == NameCheckResult.NO_MATCH) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Beneficiary name mismatch",
                    "The beneficiary name does not match the holder of the destination account");
        }

        var payment = transactions.save(new PaymentTransaction(source, target, actor.id(), command.beneficiaryName(),
                nameCheck, amount, command.reference(), key, clock.instant()));
        accounts.transfer(payment.getId(), source, target, amount);
        audit.record("PAYMENT_EXECUTED", actor.id(), actor.username(), ip,
                "tx=%s from=%s to=%s amount=%s %s nameCheck=%s".formatted(payment.getId(), fromIban, toIban,
                        amount.amount(), amount.currency(), nameCheck));
        return new PaymentResult(payment, false);
    }
}
