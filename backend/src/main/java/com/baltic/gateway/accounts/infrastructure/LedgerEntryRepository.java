package lt.baltic.gateway.accounts.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.accounts.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    Optional<LedgerEntry> findFirstByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId);
}
