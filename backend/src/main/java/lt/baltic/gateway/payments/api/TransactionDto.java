package lt.baltic.gateway.payments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lt.baltic.gateway.payments.domain.NameCheckResult;
import lt.baltic.gateway.payments.domain.PaymentTransaction;
import lt.baltic.gateway.payments.domain.TxStatus;

public record TransactionDto(UUID id, String fromIban, String toIban, String beneficiaryName, NameCheckResult nameCheck,
                             BigDecimal amount, String currency, String reference, TxStatus status, Instant createdAt) {
    public static TransactionDto from(PaymentTransaction tx) {
        return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), tx.getBeneficiaryName(), tx.getNameCheck(),
                tx.getAmount(), tx.getCurrency(), tx.getReference(), tx.getStatus(), tx.getCreatedAt());
    }
}
