package com.baltic.gateway.accounts.infrastructure.ledger;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionIdIn(Collection<UUID> transactionIds);
}
