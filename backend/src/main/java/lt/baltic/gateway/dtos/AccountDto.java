package lt.baltic.gateway.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lt.baltic.gateway.entities.Account;

public record AccountDto(UUID id, String iban, String currency, BigDecimal balance) {
    public static AccountDto from(Account account) {
        return new AccountDto(account.getId(), account.getIban(), account.getCurrency(), account.getBalance());
    }
}