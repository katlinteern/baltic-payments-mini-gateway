package lt.baltic.gateway.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lt.baltic.gateway.domain.Account;
import lt.baltic.gateway.domain.PaymentTransaction;
import lt.baltic.gateway.repo.AccountRepository;
import lt.baltic.gateway.repo.TransactionRepository;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.web.BusinessException;
import lt.baltic.gateway.web.Dtos.PaymentRequest;
import lt.baltic.gateway.web.Dtos.TransactionDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public record PaymentResult(TransactionDto transaction, boolean replay) {}
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
    public PaymentResult execute(UserPrincipal actor, PaymentRequest request, String key, String ip) {
        String fromIban = normalize(request.fromIban());
        String toIban = normalize(request.toIban());
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
                    || existing.getAmount().compareTo(request.amount()) != 0
                    || !existing.getCurrency().equals(request.currency())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse", "This key was already used for a different payment");
            }
            return new PaymentResult(TransactionDto.from(existing), true);
        }
        if (!source.getOwnerId().equals(actor.id())) throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden", "Source account is not yours");
        if (!source.getCurrency().equals(request.currency()) || !target.getCurrency().equals(request.currency())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch", "Currency must match both accounts");
        }
        BigDecimal amount = request.amount();
        if (source.getBalance().compareTo(amount) < 0) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", "Balance is too low for this payment");
        source.debit(amount);
        target.credit(amount);
        PaymentTransaction saved = transactions.save(new PaymentTransaction(source, target, actor.id(), amount,
                request.currency(), request.reference(), key));
        audit.record("PAYMENT_EXECUTED", actor.id(), actor.username(), ip,
                "tx=%s from=%s to=%s amount=%s %s".formatted(saved.getId(), fromIban, toIban, amount, request.currency()));
        return new PaymentResult(TransactionDto.from(saved), false);
    }
}
