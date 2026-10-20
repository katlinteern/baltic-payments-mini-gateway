package com.baltic.gateway.accounts.application.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import com.baltic.gateway.platform.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the ledger postings. Postings are written only by {@code TransferService}. */
@Service
public class LedgerService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public LedgerService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** Account statement, newest posting first. An account the caller may not see is reported as unknown. */
    @Transactional(readOnly = true)
    public Page<LedgerEntry> statement(UserPrincipal user, UUID accountId, int page, int size) {
        if (!accounts.existsByIdAndOwnerId(accountId, user.id())) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Unknown account");
        }
        return ledger.findByAccountId(accountId, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "sequence")));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> postingsOf(Collection<UUID> transactionIds) {
        return transactionIds.isEmpty() ? List.of() : ledger.findByTransactionIdIn(transactionIds);
    }
}
