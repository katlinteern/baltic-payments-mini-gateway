package com.baltic.gateway.accounts.application.ledger;

import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerEntryRepository ledger;

    @Transactional(readOnly = true)
    public List<LedgerEntryView> entriesOf(Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) return List.of();
        return ledger.findByTransactionIdIn(transactionIds).stream()
                .map(LedgerEntryView::of)
                .toList();
    }
}
