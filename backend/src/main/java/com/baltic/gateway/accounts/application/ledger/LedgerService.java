package com.baltic.gateway.accounts.application.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal read access to the ledger postings. The ledger is a back-office record and is not exposed to customers;
 * they see their payments through the transaction history. Postings are written only by {@code TransferService}.
 */
@Service
public class LedgerService {
    private final LedgerEntryRepository ledger;

    public LedgerService(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> postingsOf(Collection<UUID> transactionIds) {
        return transactionIds.isEmpty() ? List.of() : ledger.findByTransactionIdIn(transactionIds);
    }
}
