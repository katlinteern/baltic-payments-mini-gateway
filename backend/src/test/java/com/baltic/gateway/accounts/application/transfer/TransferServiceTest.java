package com.baltic.gateway.accounts.application.transfer;

import com.baltic.gateway.accounts.domain.account.Account;
import com.baltic.gateway.accounts.domain.ledger.EntryDirection;
import com.baltic.gateway.accounts.domain.ledger.LedgerEntry;
import com.baltic.gateway.accounts.infrastructure.account.AccountRepository;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerEntryRepository;
import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    AccountRepository accounts;

    @Mock
    LedgerEntryRepository ledger;

    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, ledger, CLOCK);
    }

    private static Account account(UUID owner, String iban, String currency, String balance) {
        return new Account(UUID.randomUUID(), owner, iban, currency, "Holder", new BigDecimal(balance));
    }

    private TransferParties parties(Account source, Account target) {
        lenient().when(accounts.findById(source.getId())).thenReturn(Optional.of(source));
        lenient().when(accounts.findById(target.getId())).thenReturn(Optional.of(target));
        return new TransferParties(source.party(), target.party());
    }

    @Test
    void locksAccountsInSortedIbanOrder() {
        var a = account(UUID.randomUUID(), "EE382200221020145685", "EUR", "1.00");
        var b = account(UUID.randomUUID(), "LT121000011101001000", "EUR", "1.00");
        when(accounts.findByIbanForUpdate("EE382200221020145685")).thenReturn(Optional.of(a));
        when(accounts.findByIbanForUpdate("LT121000011101001000")).thenReturn(Optional.of(b));

        var parties = service.lockForTransfer("LT121000011101001000", "EE382200221020145685");

        var order = inOrder(accounts);
        order.verify(accounts).findByIbanForUpdate("EE382200221020145685");
        order.verify(accounts).findByIbanForUpdate("LT121000011101001000");
        assertEquals(b.party(), parties.source());
        assertEquals(a.party(), parties.target());
    }

    @Test
    void unknownIbanIsNotFound() {
        when(accounts.findByIbanForUpdate("EE382200221020145685")).thenReturn(Optional.empty());

        var failure = assertThrows(
                BusinessException.class, () -> service.lockForTransfer("LT121000011101001000", "EE382200221020145685"));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatus());
    }

    @Test
    void transferMovesFundsAndBooksBalancedLedgerEntries() {
        var source = account(UUID.randomUUID(), "LT121000011101001000", "EUR", "100.00");
        var target = account(UUID.randomUUID(), "EE382200221020145685", "EUR", "5.00");
        UUID txId = UUID.randomUUID();

        service.transfer(txId, parties(source, target), Money.of(new BigDecimal("40.00"), "EUR"));

        assertEquals(new BigDecimal("60.00"), source.balance().amount());
        assertEquals(new BigDecimal("45.00"), target.balance().amount());
        var captor = forClass(LedgerEntry.class);
        verify(ledger, atLeastOnce()).save(captor.capture());
        var entries = captor.getAllValues();
        assertEquals(2, entries.size());
        assertEquals(EntryDirection.DEBIT, entries.get(0).getDirection());
        assertEquals(0, new BigDecimal("60.00").compareTo(entries.get(0).getBalanceAfter()));
        assertEquals(EntryDirection.CREDIT, entries.get(1).getDirection());
        assertEquals(0, new BigDecimal("45.00").compareTo(entries.get(1).getBalanceAfter()));
        assertEquals(txId, entries.get(0).getTransactionId());
    }

    @Test
    void transferRejectsOverdraftWithoutPosting() {
        var source = account(UUID.randomUUID(), "LT121000011101001000", "EUR", "10.00");
        var target = account(UUID.randomUUID(), "EE382200221020145685", "EUR", "0.00");

        var failure = assertThrows(
                BusinessException.class,
                () -> service.transfer(
                        UUID.randomUUID(), parties(source, target), Money.of(new BigDecimal("10.01"), "EUR")));

        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, failure.getCode());
        assertEquals(new BigDecimal("10.00"), source.balance().amount());
        verify(ledger, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferRejectsCurrencyMismatch() {
        var source = account(UUID.randomUUID(), "LT121000011101001000", "EUR", "10.00");
        var target = account(UUID.randomUUID(), "EE382200221020145685", "EUR", "0.00");

        var failure = assertThrows(
                BusinessException.class,
                () -> service.transfer(
                        UUID.randomUUID(), parties(source, target), Money.of(new BigDecimal("1.00"), "USD")));

        assertEquals(ErrorCode.CURRENCY_MISMATCH, failure.getCode());
    }
}
