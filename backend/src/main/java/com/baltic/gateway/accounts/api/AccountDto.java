package lt.baltic.gateway.accounts.api;

import java.math.BigDecimal;
import java.util.UUID;
import lt.baltic.gateway.accounts.domain.Account;

public record AccountDto(UUID id, String iban, String currency, String holderName, BigDecimal balance) {
    public static AccountDto from(Account account) {
        return new AccountDto(account.getId(), account.getIban(), account.getCurrency(), account.getHolderName(),
                account.balance().amount());
    }
}
