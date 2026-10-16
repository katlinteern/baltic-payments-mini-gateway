package lt.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import lt.baltic.gateway.accounts.infrastructure.AccountRepository;
import lt.baltic.gateway.payments.application.PaymentCommand;
import lt.baltic.gateway.payments.application.PaymentService;
import lt.baltic.gateway.platform.money.Money;
import lt.baltic.gateway.platform.security.Role;
import lt.baltic.gateway.platform.security.UserPrincipal;
import lt.baltic.gateway.platform.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("local")
class PaymentConcurrencyTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String ALICE_IBAN = "LT121000011101001000";
    private static final String BOB_IBAN = "EE382200221020145685";
    private static final UserPrincipal ALICE = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "alice", Role.USER);
    private static final UserPrincipal BOB = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000b1"), "bob", Role.USER);
    @Autowired PaymentService payments;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetPaymentFixtures() {
        jdbc.execute("TRUNCATE ledger_entries, transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
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

    @Test void repeatingIdempotencyKeyDoesNotDebitTwice() {
        var request = new PaymentCommand(ALICE_IBAN, BOB_IBAN, "Bob Tamm", eur("10.00"), "retry");
        var first = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        var replay = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        assertEquals(first.transaction().getId(), replay.transaction().getId());
        assertTrue(replay.replay());
        assertEquals(0, new BigDecimal("990.00").compareTo(accounts.findByIban(ALICE_IBAN).orElseThrow().getBalance()));
    }
}
