package com.baltic.gateway.accounts.application.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledger;

    public LedgerService(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<Posting> postingsOf(Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) return List.of();
        return ledger.findByTransactionIdIn(transactionIds).stream().map(Posting::of).toList();
    }
}
