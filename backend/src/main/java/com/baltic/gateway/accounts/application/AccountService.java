package com.baltic.gateway.accounts.application;

import java.time.Clock;
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
                .filter(account -> user.isPrivileged() || account.getOwnerId().equals(user.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown account"));
        return ledger.findByAccountId(accountId, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "sequence")));
    }

    @Transactional(readOnly = true)
    public Optional<String> findHolderName(String iban) {
        return accounts.findByIban(iban).map(Account::getHolderName);
    }

    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal user) {
        return user.isPrivileged() ? accounts.findAllByOrderByIban() : accounts.findByOwnerIdOrderByIban(user.id());
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

    /** Locks the accounts in sorted IBAN order so concurrent opposite transfers cannot deadlock. */
    @Transactional
    public Map<String, Account> lockForUpdate(List<String> ibans) {
        Map<String, Account> locked = new LinkedHashMap<>();
        for (String iban : new TreeSet<>(ibans)) {
            locked.put(iban, accounts.findByIbanForUpdate(iban).orElseThrow(() ->
                    new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown IBAN " + iban)));
        }
        return locked;
    }

    /** Moves funds between two locked accounts and books the matching debit and credit ledger entries. */
    @Transactional
    public void transfer(UUID transactionId, Account source, Account target, Money amount) {
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
}
