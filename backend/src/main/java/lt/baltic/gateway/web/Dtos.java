package lt.baltic.gateway.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lt.baltic.gateway.domain.Account;
import lt.baltic.gateway.domain.AuditLog;
import lt.baltic.gateway.domain.PaymentTransaction;
import lt.baltic.gateway.domain.Role;
import lt.baltic.gateway.domain.TxStatus;
import org.springframework.data.domain.Page;

public final class Dtos {
    private Dtos() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}
    public record AccountDto(UUID id, String iban, String currency, BigDecimal balance) {
        public static AccountDto from(Account account) { return new AccountDto(account.getId(), account.getIban(), account.getCurrency(), account.getBalance()); }
    }
    public record PaymentRequest(@NotBlank @ValidIban String fromIban, @NotBlank @ValidIban String toIban,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
                                 @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency, @Size(max = 140) String reference) {}
    public record TransactionDto(UUID id, String fromIban, String toIban, BigDecimal amount, String currency,
                                 String reference, TxStatus status, Instant createdAt) {
        public static TransactionDto from(PaymentTransaction tx) { return new TransactionDto(tx.getId(), tx.getFromIban(), tx.getToIban(), tx.getAmount(), tx.getCurrency(), tx.getReference(), tx.getStatus(), tx.getCreatedAt()); }
    }
    public record AuditDto(Long id, String action, UUID userId, String username, String ipAddress, String details, Instant createdAt) {
        public static AuditDto from(AuditLog log) { return new AuditDto(log.getId(), log.getAction(), log.getUserId(), log.getUsername(), log.getIpAddress(), log.getDetails(), log.getCreatedAt()); }
    }
    public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {
        public static <T> PageResponse<T> from(Page<T> result) { return new PageResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()); }
    }
}
