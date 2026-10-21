package com.baltic.gateway.accounts.api;

import java.math.BigDecimal;
import java.util.UUID;
import com.baltic.gateway.accounts.domain.account.Account;

public record AccountDto(UUID id, String iban, String currency, String holderName, BigDecimal balance) {
    public static AccountDto from(Account account) {
        return new AccountDto(account.getId(), account.getIban(), account.getCurrency(), account.getHolderName(),
                account.balance().amount());
    }
}
