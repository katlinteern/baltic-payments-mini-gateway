package lt.baltic.gateway.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lt.baltic.gateway.enums.TxStatus;
import lt.baltic.gateway.entities.PaymentTransaction;

public record TransactionDto(UUID id, String fromIban, String toIban, String beneficiaryName, BigDecimal amount, String currency,
                            String reference, TxStatus status, Instant createdAt) {
    public static TransactionDto from(PaymentTransaction tx) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), tx.getBeneficiaryName(), tx.getAmount(), tx.getCurrency(),
                tx.getReference(), tx.getStatus(), tx.getCreatedAt());
    }
}