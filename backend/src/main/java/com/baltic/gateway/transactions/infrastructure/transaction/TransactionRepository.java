package com.baltic.gateway.transactions.infrastructure.transaction;

import com.baltic.gateway.transactions.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);
}
