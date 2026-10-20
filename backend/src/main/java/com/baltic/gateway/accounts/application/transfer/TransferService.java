package com.baltic.gateway.accounts.application.transfer;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves funds between accounts: the only place where balances change, always together with the ledger postings. */
@Service
public class TransferService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    public TransferService(AccountRepository accounts, LedgerEntryRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * Advisory check for the review step, without any lock: both accounts exist, share the payment's currency and the
     * source can cover the amount. The payment itself repeats these checks under the row locks.
     */
    @Transactional(readOnly = true)
    public void checkTransferable(String fromIban, String toIban, Money amount) {
        Account source = accounts.findByIban(fromIban).orElseThrow(() -> unknownIban(fromIban));
        Account target = accounts.findByIban(toIban).orElseThrow(() -> unknownIban(toIban));
        requireTransferable(source, target, amount);
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
        requireTransferable(source, target, amount);
        source.debit(amount);
        target.credit(amount);
        var now = clock.instant();
        ledger.save(new LedgerEntry(transactionId, source, EntryDirection.DEBIT, amount, now));
        ledger.save(new LedgerEntry(transactionId, target, EntryDirection.CREDIT, amount, now));
    }

    private static void requireTransferable(Account source, Account target, Money amount) {
        if (!source.getCurrency().equals(amount.currency()) || !target.getCurrency().equals(amount.currency())) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, "Currency must match both accounts");
        }
        if (!source.canCover(amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Balance is too low for this payment");
        }
    }

    /** Locks in sorted IBAN order so concurrent opposite transfers cannot deadlock. */
    private Map<String, Account> lock(String... ibans) {
        Map<String, Account> locked = new LinkedHashMap<>();
        for (String iban : new TreeSet<>(List.of(ibans))) {
            locked.put(iban, accounts.findByIbanForUpdate(iban).orElseThrow(() -> unknownIban(iban)));
        }
        return locked;
    }

    private static BusinessException unknownIban(String iban) {
        return new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown IBAN " + iban);
    }
}
