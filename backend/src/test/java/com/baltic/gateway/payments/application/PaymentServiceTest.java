package com.baltic.gateway.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.accounts.application.AccountService;
import com.baltic.gateway.accounts.domain.Account;
import com.baltic.gateway.accounts.infrastructure.AccountRepository;
import com.baltic.gateway.accounts.infrastructure.LedgerEntryRepository;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.payments.domain.NameCheckResult;
import com.baltic.gateway.payments.domain.PaymentTransaction;
import com.baltic.gateway.payments.infrastructure.TransactionRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.Role;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final String FROM = "LT121000011101001000";
    private static final String TO = "EE382200221020145685";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    @Mock AccountRepository accountRepository;
    @Mock LedgerEntryRepository ledger;
    @Mock TransactionRepository transactions;
    @Mock AuditService audit;
    @Mock PlatformTransactionManager transactionManager;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private PaymentService service;
    private final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "alice", Role.USER);
    private Account source;
    private Account target;

    private double payments(String outcome, String reason) {
        return registry.counter("payments", "outcome", outcome, "currency", "EUR", "reason", reason).count();
    }

    @BeforeEach void setUp() {
        service = new PaymentService(new AccountService(accountRepository, ledger, CLOCK), transactions, audit,
                transactionManager, CLOCK, new GatewayMetrics(registry));
        source = new Account(UUID.randomUUID(), user.id(), FROM, "EUR", "Alice Kask", new BigDecimal("100.00"));
        target = new Account(UUID.randomUUID(), UUID.randomUUID(), TO, "EUR", "Bob Tamm", BigDecimal.ZERO);
        lenient().when(accountRepository.findByIbanForUpdate(FROM)).thenReturn(Optional.of(source));
        lenient().when(accountRepository.findByIbanForUpdate(TO)).thenReturn(Optional.of(target));
        lenient().when(accountRepository.existsByIbanAndOwnerId(FROM, user.id())).thenReturn(true);
        lenient().when(transactions.findByCreatedByAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(transactions.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Money eur(String amount) {
        return Money.of(new BigDecimal(amount), "EUR");
    }

    private static PaymentCommand command(String name, Money amount, String reference) {
        return new PaymentCommand(FROM, TO, name, amount, reference);
    }

    @Test void movesFundsPostsLedgerAndAudits() {
        var result = service.execute(user, command("Bob Tamm", eur("40.00"), "invoice"), "key-12345678", "127.0.0.1");

        assertEquals(new BigDecimal("60.00"), source.balance().amount());
        assertEquals(new BigDecimal("40.00"), target.balance().amount());
        assertFalse(result.replay());
        assertEquals(NameCheckResult.MATCH, result.transaction().getNameCheck());
        verify(ledger, org.mockito.Mockito.times(2)).save(any());
        verify(audit).record(eq("PAYMENT_EXECUTED"), eq(user.id()), eq("alice"), eq("127.0.0.1"), any());
        assertEquals(1.0, payments("completed", "none"));
        assertEquals(40.0, registry.counter("payments.value", "currency", "EUR").count());
        assertEquals(1, registry.timer("payments.processing").count());
    }

    @Test void closeNameMatchIsAllowedAndRecorded() {
        var result = service.execute(user, command("Bob", eur("1.00"), null), "key-12345678", "127.0.0.1");

        assertEquals(NameCheckResult.CLOSE_MATCH, result.transaction().getNameCheck());
    }

    @Test void rejectsWrongBeneficiaryNameAndAuditsRejection() {
        var failure = assertThrows(BusinessException.class,
                () -> service.execute(user, command("Mallory Evil", eur("1.00"), null), "key-12345678", "127.0.0.1"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatus());
        assertEquals(new BigDecimal("100.00"), source.balance().amount());
        verify(transactions, never()).save(any());
        verify(audit).recordIndependent("PAYMENT_REJECTED", user.id(), "alice", "127.0.0.1", "Beneficiary name mismatch");
        assertEquals(1.0, payments("rejected", "beneficiary_name_mismatch"));
        assertEquals(0.0, payments("completed", "none"));
    }

    @Test void nameMismatchIsAcceptedOnlyWhenThePayerAcknowledgedIt() {
        var acknowledged = new PaymentCommand(FROM, TO, "Mallory Evil", eur("1.00"), null, true);

        var result = service.execute(user, acknowledged, "key-12345678", "127.0.0.1");

        assertEquals(NameCheckResult.NO_MATCH, result.transaction().getNameCheck());
        verify(audit).record(eq("PAYMENT_EXECUTED"), eq(user.id()), eq("alice"), eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.contains("mismatchAcknowledgedByPayer=true"));
    }

    @Test void rejectsInsufficientFunds() {
        var failure = assertThrows(BusinessException.class,
                () -> service.execute(user, command("Bob Tamm", eur("100.01"), null), "key-12345678", "127.0.0.1"));

        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, failure.getCode());
        verify(ledger, never()).save(any());
        verify(audit).recordIndependent(eq("PAYMENT_REJECTED"), any(), any(), any(), eq("Insufficient funds"));
    }

    @Test void rejectsForeignSourceAccount() {
        var mallory = new UserPrincipal(UUID.randomUUID(), "mallory", Role.USER);

        var failure = assertThrows(BusinessException.class,
                () -> service.execute(mallory, command("Bob Tamm", eur("1.00"), null), "foreign-key-001", "127.0.0.1"));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatus());
        verify(transactions, never()).save(any());
        verify(accountRepository, never()).findByIbanForUpdate(any());
    }

    @Test void rejectsCurrencyMismatch() {
        var failure = assertThrows(BusinessException.class, () -> service.execute(user,
                command("Bob Tamm", Money.of(new BigDecimal("1.00"), "USD"), null), "currency-key-001", "127.0.0.1"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatus());
    }

    @Test void validationErrorsAreNotAudited() {
        var sameAccount = new PaymentCommand(FROM, FROM, "Alice Kask", eur("1.00"), null);

        var failure = assertThrows(BusinessException.class,
                () -> service.execute(user, sameAccount, "same-account-key", "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        verify(audit, never()).recordIndependent(any(), any(), any(), any(), any());
    }

    @Test void matchingIdempotencyRequestReplaysWithoutChangingBalances() {
        var existing = new PaymentTransaction(source, target, user.id(), "Bob Tamm", NameCheckResult.MATCH,
                eur("40.00"), "invoice", "key-12345678", CLOCK.instant());
        when(transactions.findByCreatedByAndIdempotencyKey(user.id(), "key-12345678")).thenReturn(Optional.of(existing));

        var result = service.execute(user, command("Bob Tamm", eur("40.00"), "invoice"), "key-12345678", "127.0.0.1");

        assertTrue(result.replay());
        assertEquals(new BigDecimal("100.00"), source.balance().amount());
        assertEquals(1.0, payments("replayed", "none"));
        assertEquals(0.0, payments("completed", "none"));
        verify(transactions, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test void rejectsIdempotencyKeyReuseWithDifferentReference() {
        var existing = new PaymentTransaction(source, target, user.id(), "Bob Tamm", NameCheckResult.MATCH,
                eur("40.00"), "invoice-1", "key-12345678", CLOCK.instant());
        when(transactions.findByCreatedByAndIdempotencyKey(user.id(), "key-12345678")).thenReturn(Optional.of(existing));

        var failure = assertThrows(BusinessException.class,
                () -> service.execute(user, command("Bob Tamm", eur("40.00"), "invoice-2"), "key-12345678", "127.0.0.1"));

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSE, failure.getCode());
    }
}
