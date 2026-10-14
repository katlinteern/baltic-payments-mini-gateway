package lt.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import lt.baltic.gateway.enums.Role;
import lt.baltic.gateway.repositories.AccountRepository;
import lt.baltic.gateway.security.UserPrincipal;
import lt.baltic.gateway.services.PaymentService;
import lt.baltic.gateway.services.PaymentCommand;
import lt.baltic.gateway.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PaymentConcurrencyTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String ALICE_IBAN = "LT121000011101001000";
    private static final String BOB_IBAN = "EE382200221020145685";
    private static final UserPrincipal ALICE = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "alice", Role.USER);
    @Autowired PaymentService payments;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetPaymentFixtures() {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
    }

    @Test void simultaneousTransfersCannotOverdraw() throws Exception {
        int count = 12;
        var executor = Executors.newFixedThreadPool(count);
        var start = new CountDownLatch(1);
        var succeeded = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int request = i;
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    payments.execute(ALICE, new PaymentCommand(ALICE_IBAN, BOB_IBAN, new BigDecimal("100.00"), "EUR", "race"), "race-key-" + request + "-0000", "127.0.0.1");
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
    }
    @Test void repeatingIdempotencyKeyDoesNotDebitTwice() {
        var request = new PaymentCommand(ALICE_IBAN, BOB_IBAN, new BigDecimal("10.00"), "EUR", "retry");
        var first = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        var replay = payments.execute(ALICE, request, "same-key-0000001", "127.0.0.1");
        assertEquals(first.transaction().getId(), replay.transaction().getId());
        assertEquals(true, replay.replay());
    }
}
