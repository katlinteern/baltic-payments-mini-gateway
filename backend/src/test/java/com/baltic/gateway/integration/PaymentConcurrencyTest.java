package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.baltic.gateway.accounts.infrastructure.AccountRepository;
import com.baltic.gateway.payments.application.PaymentCommand;
import com.baltic.gateway.payments.application.PaymentService;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.BusinessException;
import com.baltic.gateway.platform.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.ledger.reconciliation.enabled=false")
@Testcontainers
@ActiveProfiles("local")
class PaymentConcurrencyTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String ALICE_IBAN = "LT121000011101001000";
    private static final String BOB_IBAN = "EE382200221020145685";
    private static final String ALICE_SECOND_IBAN = "LV80BANK0000435195001";
    private static final String BOB_SECOND_IBAN = "LT601010012345678901";
    private static final UserPrincipal ALICE = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "alice");
    private static final UserPrincipal BOB = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000b1"), "bob");
    @Autowired PaymentService payments;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach void resetPaymentFixtures() {
        jdbc.execute("TRUNCATE payment_intents, ledger_entries, transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("500.00"), ALICE_SECOND_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("750.00"), BOB_SECOND_IBAN);
    }

    private static Money eur(String amount) {
        return Money.of(new BigDecimal(amount), "EUR");
    }

    @Test void simultaneousTransfersCannotOverdraw() throws Exception {
        int count = 12;
        var executor = Executors.newFixedThreadPool(count);
        var start = new CountDownLatch(1);
        var succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int request = i;
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    payments.execute(ALICE, new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("100.00"), "race"),
                            "race-key-" + request + "-0000", "127.0.0.1");
                    succeeded.incrementAndGet();
                } catch (BusinessException ignored) { }
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) future.get();
        executor.shutdown();
        assertEquals(10, succeeded.get());
        assertEquals(0, BigDecimal.ZERO.compareTo(accounts.findByIban(ALICE_IBAN).orElseThrow().getBalance()));
        assertEquals(20, jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class));
    }

    @Test void oppositeTransfersAtTheSameTimeDoNotDeadlock() throws Exception {
        int perDirection = 10;
        var executor = Executors.newFixedThreadPool(perDirection * 2);
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < perDirection; i++) {
            final int request = i;
            futures.add(executor.submit(() -> {
                start.await();
                payments.execute(ALICE, new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "a-to-b"), "a2b-key-" + request + "-000000", "127.0.0.1");
                return null;
            }));
            futures.add(executor.submit(() -> {
                start.await();
                payments.execute(BOB, new PaymentCommand(BOB_IBAN, ALICE_IBAN, "Alice Kask", eur("10.00"), "b-to-a"), "b2a-key-" + request + "-000000", "127.0.0.1");
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) future.get();
        executor.shutdown();
        assertEquals(0, new BigDecimal("1000.00").compareTo(accounts.findByIban(ALICE_IBAN).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(accounts.findByIban(BOB_IBAN).orElseThrow().getBalance()));
    }

    /** Holds a row lock on the account in another transaction until the returned handle is closed. */
    private AutoCloseable holdRowLock(String iban) throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        Future<?> finished = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accounts.findByIbanForUpdate(iban);
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(locked.await(10, TimeUnit.SECONDS));
        return () -> {
            release.countDown();
            finished.get(10, TimeUnit.SECONDS);
            holder.shutdown();
        };
    }

    @Test void paymentGivesUpWhenAnAccountStaysLockedAndLeavesBalancesUntouched() throws Exception {
        try (var ignored = holdRowLock(BOB_IBAN)) {
            long started = System.nanoTime();

            assertThrows(PessimisticLockingFailureException.class, () -> payments.execute(ALICE,
                    new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "locked"), "locked-key-000001", "127.0.0.1"));

            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(10)) < 0);
        }
        assertEquals(0, new BigDecimal("1000.00").compareTo(accounts.findByIban(ALICE_IBAN).orElseThrow().getBalance()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test void payingFromSomeoneElsesAccountIsRejectedWithoutWaitingForItsLock() throws Exception {
        try (var ignored = holdRowLock(ALICE_IBAN)) {
            long started = System.nanoTime();

            var failure = assertThrows(BusinessException.class, () -> payments.execute(BOB,
                    new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "theft"), "theft-key-0000001", "127.0.0.1"));

            assertEquals(HttpStatus.FORBIDDEN, failure.getStatus());
            // The lock timeout is 3 s; a rejection that did not queue behind the lock returns well before that.
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
        }
    }

    @Test void replayIsAnsweredWithoutWaitingForAnAccountLock() throws Exception {
        var request = new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "retry");
        var first = payments.execute(ALICE, request, "replay-key-00001", "127.0.0.1");

        try (var ignored = holdRowLock(BOB_IBAN)) {
            long started = System.nanoTime();

            var replay = payments.execute(ALICE, request, "replay-key-00001", "127.0.0.1");

            assertTrue(replay.replay());
            assertEquals(first.transaction().getId(), replay.transaction().getId());
            // The lock timeout is 3 s; an answer that did not queue behind the lock returns well before that.
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
        }
    }

    @Test void sameKeyOnDisjointAccountsBooksOnePaymentAndRejectsTheOther() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                final String key = "disjoint-key-" + round + "-00";
                var start = new CountDownLatch(1);
                var first = executor.submit(() -> {
                    start.await();
                    return payments.execute(ALICE, new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "one"), key, "127.0.0.1");
                });
                var second = executor.submit(() -> {
                    start.await();
                    return payments.execute(ALICE, new PaymentCommand(ALICE_SECOND_IBAN, BOB_SECOND_IBAN, "Bob Tamm", eur("10.00"), "two"), key, "127.0.0.1");
                });
                start.countDown();

                int booked = 0;
                for (var future : List.of(first, second)) {
                    try {
                        future.get();
                        booked++;
                    } catch (java.util.concurrent.ExecutionException ex) {
                        var failure = (BusinessException) ex.getCause();
                        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSE, failure.getCode());
                    }
                }
                assertEquals(1, booked);
            }
        } finally {
            executor.shutdown();
        }
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test void repeatingIdempotencyKeyDoesNotDebitTwice() {
        var request = new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "retry");
        var first = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        var replay = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        assertEquals(first.transaction().getId(), replay.transaction().getId());
        assertTrue(replay.replay());
        assertEquals(0, new BigDecimal("990.00").compareTo(accounts.findByIban(ALICE_IBAN).orElseThrow().getBalance()));
    }
}
