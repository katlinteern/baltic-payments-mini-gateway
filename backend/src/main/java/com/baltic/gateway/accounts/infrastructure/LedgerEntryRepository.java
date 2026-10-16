package com.baltic.gateway.accounts.infrastructure;

import java.util.UUID;
import com.baltic.gateway.accounts.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);
}
