package lt.baltic.gateway.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lt.baltic.gateway.entity.Account;
import lt.baltic.gateway.entity.PaymentTransaction;
import lt.baltic.gateway.repository.AccountRepository;
import lt.baltic.gateway.repository.TransactionRepository;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public record PaymentResult(PaymentTransaction transaction, boolean replay) {}
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;
    public PaymentService(AccountRepository accounts, TransactionRepository transactions, AuditService audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.audit = audit;
    }
    public static String normalize(String iban) { return iban.replaceAll("\\s", "").toUpperCase(); }

    @Transactional
    public PaymentResult execute(UserPrincipal actor, PaymentCommand command, String key, String ip) {
        String fromIban = normalize(command.fromIban());
        String toIban = normalize(command.toIban());
        BigDecimal amount = command.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid amount", "Payment amount must be greater than zero");
        }
        if (fromIban.equals(toIban)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid transfer", "Source and destination accounts must differ");

        Map<String, Account> locked = new HashMap<>();
        for (String iban : new TreeSet<>(List.of(fromIban, toIban))) {
            locked.put(iban, accounts.findByIbanForUpdate(iban).orElseThrow(() ->
                    new BusinessException(HttpStatus.NOT_FOUND, "Account not found", "Unknown IBAN " + iban)));
        }
        Account source = locked.get(fromIban);
        Account target = locked.get(toIban);
        var prior = transactions.findByCreatedByAndIdempotencyKey(actor.id(), key);
        if (prior.isPresent()) {
            PaymentTransaction existing = prior.get();
            if (!existing.getFromIban().equals(fromIban) || !existing.getToIban().equals(toIban)
                    || existing.getAmount().compareTo(amount) != 0
                    || !existing.getCurrency().equals(command.currency())
                    || !Objects.equals(existing.getReference(), command.reference())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse", "This key was already used for a different payment");
            }
            return new PaymentResult(existing, true);
        }
        if (!source.getOwnerId().equals(actor.id())) throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden", "Source account is not yours");
        if (!source.getCurrency().equals(command.currency()) || !target.getCurrency().equals(command.currency())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch", "Currency must match both accounts");
        }
        if (source.getBalance().compareTo(amount) < 0) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", "Balance is too low for this payment");
        source.debit(amount);
        target.credit(amount);
        PaymentTransaction saved = transactions.save(new PaymentTransaction(source, target, actor.id(), amount,
                command.currency(), command.reference(), key));
        audit.record("PAYMENT_EXECUTED", actor.id(), actor.username(), ip,
            "tx=%s from=%s to=%s amount=%s %s".formatted(saved.getId(), fromIban, toIban, amount, command.currency()));
        return new PaymentResult(saved, false);
    }
}
