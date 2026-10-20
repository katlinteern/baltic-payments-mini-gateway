package com.baltic.gateway.accounts.infrastructure.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.iban = :iban")
    Optional<Account> findByIbanForUpdate(@Param("iban") String iban);

    Optional<Account> findByIban(String iban);

    /** Answers without loading the entity, so no stale copy ends up in the session before the row is locked. */
    boolean existsByIbanAndOwnerId(String iban, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    List<Account> findByOwnerIdOrderByIban(UUID ownerId);
}
