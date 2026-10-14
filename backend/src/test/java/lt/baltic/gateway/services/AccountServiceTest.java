package lt.baltic.gateway.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lt.baltic.gateway.entities.Account;
import lt.baltic.gateway.enums.Role;
import lt.baltic.gateway.repositories.AccountRepository;
import lt.baltic.gateway.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock AccountRepository accounts;
    @InjectMocks AccountService service;

    @Test void userOnlyReceivesOwnedAccounts() {
        UUID ownerId = UUID.randomUUID();
        var owned = List.of(new Account(UUID.randomUUID(), ownerId, "LT121000011101001000", "EUR", new BigDecimal("10.00")));
        when(accounts.findByOwnerIdOrderByIban(ownerId)).thenReturn(owned);

        assertEquals(owned, service.list(new UserPrincipal(ownerId, "alice", Role.USER)));
        verify(accounts).findByOwnerIdOrderByIban(ownerId);
    }

    @Test void auditorReceivesAllAccounts() {
        var allAccounts = List.of(
                new Account(UUID.randomUUID(), UUID.randomUUID(), "LT121000011101001000", "EUR", new BigDecimal("10.00")),
                new Account(UUID.randomUUID(), UUID.randomUUID(), "EE382200221020145685", "EUR", new BigDecimal("20.00")));
        when(accounts.findAllByOrderByIban()).thenReturn(allAccounts);

        assertEquals(allAccounts, service.list(new UserPrincipal(UUID.randomUUID(), "auditor", Role.AUDITOR)));
        verify(accounts).findAllByOrderByIban();
    }
}