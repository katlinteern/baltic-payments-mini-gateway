package com.baltic.gateway.accounts.application.account;

import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.platform.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    AccountRepository accounts;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts);
    }

    private static Account account(UUID owner, String iban, String currency, String balance) {
        return new Account(UUID.randomUUID(), owner, iban, currency, "Holder", new BigDecimal(balance));
    }

    @Test
    void userOnlyReceivesOwnedAccounts() {
        UUID ownerId = UUID.randomUUID();
        var owned = List.of(account(ownerId, "LT121000011101001000", "EUR", "10.00"));
        when(accounts.findByOwnerIdOrderByIban(ownerId)).thenReturn(owned);

        assertEquals(owned, service.list(new UserPrincipal(ownerId, "alice")));
        verify(accounts).findByOwnerIdOrderByIban(ownerId);
    }
}
