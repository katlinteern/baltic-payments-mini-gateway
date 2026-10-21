package com.baltic.gateway.transactions.application.recording;

import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.transactions.domain.transaction.Transaction;
import com.baltic.gateway.transactions.infrastructure.transaction.TransactionRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class TransactionRecordingService {
    private final TransactionRepository repository;

    public TransactionRecordingService(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction record(@NonNull Transaction transaction) {
        return repository.save(transaction);
    }

    public Optional<Transaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey) {
        return repository.findByCreatedByAndIdempotencyKey(createdBy, idempotencyKey);
    }
}
