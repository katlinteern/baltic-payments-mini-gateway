package lt.baltic.gateway.repositories;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.iban = :iban")
    Optional<Account> findByIbanForUpdate(@Param("iban") String iban);
    Optional<Account> findByIban(String iban);
    List<Account> findByOwnerIdOrderByIban(UUID ownerId);
    List<Account> findAllByOrderByIban();
}
