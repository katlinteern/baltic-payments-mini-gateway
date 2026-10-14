package lt.baltic.gateway.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.entities.Account;
import lt.baltic.gateway.entities.PaymentTransaction;
import lt.baltic.gateway.enums.Role;
import lt.baltic.gateway.repositories.AccountRepository;
import lt.baltic.gateway.repositories.TransactionRepository;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final String FROM = "LT121000011101001000";
    private static final String TO = "EE382200221020145685";
    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;
    @Mock AuditService audit;
    private PaymentService service;
    private final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "alice", Role.USER);
    private Account source;
    private Account target;
    @BeforeEach void setUp() {
        service = new PaymentService(accounts, transactions, audit);
        source = new Account(UUID.randomUUID(), user.id(), FROM, "EUR", new BigDecimal("100.00"));
        target = new Account(UUID.randomUUID(), UUID.randomUUID(), TO, "EUR", BigDecimal.ZERO);
        when(accounts.findByIbanForUpdate(FROM)).thenReturn(Optional.of(source));
        when(accounts.findByIbanForUpdate(TO)).thenReturn(Optional.of(target));
        when(transactions.findByCreatedByAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
    }
    @Test void movesFundsAndAudits() {
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.execute(user, new PaymentCommand(FROM, TO, new BigDecimal("40.00"), "EUR", "invoice"), "key-12345678", "127.0.0.1");
        assertEquals(0, new BigDecimal("60.00").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("40.00").compareTo(target.getBalance()));
        assertEquals(false, result.replay());
        assertEquals(0, new BigDecimal("40.00").compareTo(result.transaction().getAmount()));
        var lockOrder = inOrder(accounts);
        lockOrder.verify(accounts).findByIbanForUpdate(TO);
        lockOrder.verify(accounts).findByIbanForUpdate(FROM);
        verify(audit).record(any(), any(), any(), any(), any());
    }
    @Test void rejectsInsufficientFunds() {
        assertThrows(BusinessException.class, () -> service.execute(user, new PaymentCommand(FROM, TO, new BigDecimal("100.01"), "EUR", null), "key-12345678", "127.0.0.1"));
        verify(transactions, never()).save(any());
    }

    @Test void rejectsForeignSourceAccount() {
        var otherUser = new UserPrincipal(UUID.randomUUID(), "mallory", Role.USER);
        var failure = assertThrows(BusinessException.class, () -> service.execute(otherUser,
                new PaymentCommand(FROM, TO, new BigDecimal("1.00"), "EUR", null), "foreign-key-001", "127.0.0.1"));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, failure.getStatus());
        verify(transactions, never()).save(any());
    }

    @Test void rejectsCurrencyMismatch() {
        var failure = assertThrows(BusinessException.class, () -> service.execute(user,
                new PaymentCommand(FROM, TO, new BigDecimal("1.00"), "USD", null), "currency-key-001", "127.0.0.1"));
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatus());
        verify(transactions, never()).save(any());
    }

    @Test void matchingIdempotencyRequestReplaysWithoutChangingBalances() {
        var existing = new PaymentTransaction(source, target, user.id(), new BigDecimal("40.00"), "EUR", "invoice", "key-12345678");
        when(transactions.findByCreatedByAndIdempotencyKey(user.id(), "key-12345678"))
                .thenReturn(Optional.of(existing));

        var result = service.execute(user,
                new PaymentCommand(FROM, TO, new BigDecimal("40.00"), "EUR", "invoice"),
                "key-12345678", "127.0.0.1");

        assertEquals(true, result.replay());
        assertEquals(0, new BigDecimal("100.00").compareTo(source.getBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(target.getBalance()));
        verify(transactions, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test void rejectsIdempotencyKeyReuseWithDifferentReference() {
        var existing = new PaymentTransaction(source, target, user.id(), new BigDecimal("40.00"), "EUR", "invoice-1", "key-12345678");
        when(transactions.findByCreatedByAndIdempotencyKey(user.id(), "key-12345678"))
                .thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.execute(user,
                new PaymentCommand(FROM, TO, new BigDecimal("40.00"), "EUR", "invoice-2"),
                "key-12345678", "127.0.0.1"));
    }
}
