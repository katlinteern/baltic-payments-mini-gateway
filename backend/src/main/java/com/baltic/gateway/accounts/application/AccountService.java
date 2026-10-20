package com.baltic.gateway.accounts.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.Account;
import com.baltic.gateway.accounts.domain.EntryDirection;
import com.baltic.gateway.accounts.domain.LedgerEntry;
import com.baltic.gateway.accounts.infrastructure.AccountRepository;
import com.baltic.gateway.accounts.infrastructure.LedgerEntryRepository;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import com.baltic.gateway.platform.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    /** Account statement, newest posting first. An account the caller may not see is reported as unknown. */
    @Transactional(readOnly = true)
    public Page<LedgerEntry> statement(UserPrincipal user, UUID accountId, int page, int size) {
        accounts.findById(accountId)
                .filter(account -> account.getOwnerId().equals(user.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown account"));
        return ledger.findByAccountId(accountId, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "sequence")));
    }

    @Transactional(readOnly = true)
    public Optional<String> findHolderName(String iban) {
        return accounts.findByIban(iban).map(Account::getHolderName);
    }

    /** Registered holder names by account id; ids that do not exist are simply absent. */
    @Transactional(readOnly = true)
    public Map<UUID, String> holderNames(Collection<UUID> accountIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        accounts.findAllById(accountIds).forEach(account -> names.put(account.getId(), account.getHolderName()));
        return names;
    }

    /** The postings (debit and credit) booked for the given transactions. */
    @Transactional(readOnly = true)
    public List<LedgerEntry> postingsOf(Collection<UUID> transactionIds) {
        return transactionIds.isEmpty() ? List.of() : ledger.findByTransactionIdIn(transactionIds);
    }

    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal user) {
        return accounts.findByOwnerIdOrderByIban(user.id());
    }

    /**
     * Checks ownership without taking a lock, so a caller who does not own the account can never make others wait.
     * An unknown IBAN gets the same answer as a foreign one, which does not reveal which accounts exist.
     */
    @Transactional(readOnly = true)
    public void requireOwnedBy(String iban, UUID ownerId) {
        if (!accounts.existsByIbanAndOwnerId(iban, ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Source account is not yours");
        }
    }

    /**
     * Advisory check for the review step, without any lock: both accounts exist, share the payment's currency and the
     * source can cover the amount. The payment itself repeats these checks under the row locks.
     */
    @Transactional(readOnly = true)
    public void checkTransferable(String fromIban, String toIban, Money amount) {
        Account source = accounts.findByIban(fromIban).orElseThrow(() ->
                new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown IBAN " + fromIban));
        Account target = accounts.findByIban(toIban).orElseThrow(() ->
                new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown IBAN " + toIban));
        if (!source.getCurrency().equals(amount.currency()) || !target.getCurrency().equals(amount.currency())) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, "Currency must match both accounts");
        }
        if (!source.canCover(amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Balance is too low for this payment");
        }
    }

    /** Locks both accounts until the surrounding transaction ends and describes them to the caller. */
    @Transactional
    public TransferParties lockForTransfer(String fromIban, String toIban) {
        var locked = lock(fromIban, toIban);
        return new TransferParties(locked.get(fromIban).party(), locked.get(toIban).party());
    }

    /**
     * Moves funds between two accounts and books the matching debit and credit ledger entries. Takes the row locks
     * again (a no-op inside the transaction that already holds them), so it is safe even when called on its own.
     */
    @Transactional
    public void transfer(UUID transactionId, String fromIban, String toIban, Money amount) {
        var locked = lock(fromIban, toIban);
        var source = locked.get(fromIban);
        var target = locked.get(toIban);
        if (!source.getCurrency().equals(amount.currency()) || !target.getCurrency().equals(amount.currency())) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, "Currency must match both accounts");
        }
        if (!source.canCover(amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Balance is too low for this payment");
        }
        source.debit(amount);
        target.credit(amount);
        var now = clock.instant();
        ledger.save(new LedgerEntry(transactionId, source, EntryDirection.DEBIT, amount, now));
        ledger.save(new LedgerEntry(transactionId, target, EntryDirection.CREDIT, amount, now));
    }

    /** Locks in sorted IBAN order so concurrent opposite transfers cannot deadlock. */
    private Map<String, Account> lock(String... ibans) {
        Map<String, Account> locked = new LinkedHashMap<>();
        for (String iban : new TreeSet<>(List.of(ibans))) {
            locked.put(iban, accounts.findByIbanForUpdate(iban).orElseThrow(() ->
                    new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown IBAN " + iban)));
        }
        return locked;
    }
}
