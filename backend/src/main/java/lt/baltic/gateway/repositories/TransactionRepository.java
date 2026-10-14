package lt.baltic.gateway.repositories;

import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.entities.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {
    Optional<PaymentTransaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);
}
