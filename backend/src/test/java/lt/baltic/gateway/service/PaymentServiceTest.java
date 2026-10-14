package lt.baltic.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.domain.Account;
import lt.baltic.gateway.domain.PaymentTransaction;
import lt.baltic.gateway.domain.Role;
import lt.baltic.gateway.repo.AccountRepository;
import lt.baltic.gateway.repo.TransactionRepository;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.web.BusinessException;
import lt.baltic.gateway.web.Dtos.PaymentRequest;
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
        var result = service.execute(user, new PaymentRequest(FROM, TO, new BigDecimal("40.00"), "EUR", "invoice"), "key-12345678", "127.0.0.1");
        assertEquals(0, new BigDecimal("60.00").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("40.00").compareTo(target.getBalance()));
        assertEquals(false, result.replay());
        verify(audit).record(any(), any(), any(), any(), any());
    }
    @Test void rejectsInsufficientFunds() {
        assertThrows(BusinessException.class, () -> service.execute(user, new PaymentRequest(FROM, TO, new BigDecimal("100.01"), "EUR", null), "key-12345678", "127.0.0.1"));
        verify(transactions, never()).save(any());
    }
}
