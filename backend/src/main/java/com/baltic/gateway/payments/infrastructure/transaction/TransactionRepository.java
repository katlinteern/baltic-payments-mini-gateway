package com.baltic.gateway.payments.infrastructure.transaction;

import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.payments.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);
}
