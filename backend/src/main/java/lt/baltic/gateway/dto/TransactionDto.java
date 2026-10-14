package lt.baltic.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lt.baltic.gateway.domain.TxStatus;
import lt.baltic.gateway.entity.PaymentTransaction;

public record TransactionDto(UUID id, String fromIban, String toIban, BigDecimal amount, String currency,
                            String reference, TxStatus status, Instant createdAt) {
    public static TransactionDto from(PaymentTransaction tx) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), tx.getAmount(), tx.getCurrency(),
                tx.getReference(), tx.getStatus(), tx.getCreatedAt());
    }
}