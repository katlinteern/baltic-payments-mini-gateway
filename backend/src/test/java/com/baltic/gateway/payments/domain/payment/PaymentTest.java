package com.baltic.gateway.payments.domain.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.transactions.domain.transaction.NameCheckResult;
import com.baltic.gateway.platform.money.Money;
import org.junit.jupiter.api.Test;

class PaymentTest {
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Test
    void completesOnlyAfterAuthenticationAndClearsTheVerificationCode() {
        Payment payment = payment();
        UUID transactionId = UUID.randomUUID();
        payment.startAuthentication(false, "1234", NOW, NOW.plusSeconds(120));

        payment.complete(transactionId, NOW.plusSeconds(3));

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(transactionId, payment.getTransactionId());
        assertNull(payment.getVerificationCode());
    }

    @Test
    void rejectsTransitionsThatDoNotMatchTheCurrentStatus() {
        Payment payment = payment();

        assertThrows(IllegalStateException.class, () -> payment.complete(UUID.randomUUID(), NOW));
        assertThrows(IllegalStateException.class, () -> payment.fail("ERROR", "Failed", NOW));
        payment.startAuthentication(false, "1234", NOW, NOW.plusSeconds(120));
        assertThrows(IllegalStateException.class,
                () -> payment.startAuthentication(false, "5678", NOW, NOW.plusSeconds(120)));
    }

    @Test
    void expiresAtTheDeadlineAndClearsTheVerificationCode() {
        Payment payment = payment();
        Instant deadline = NOW.plusSeconds(120);
        payment.startAuthentication(false, "1234", NOW, deadline);

        assertTrue(payment.expireIfDue(deadline));
        assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
        assertNull(payment.getVerificationCode());
        assertFalse(payment.expireIfDue(deadline.plusSeconds(1)));
    }

    private static Payment payment() {
        return new Payment(UUID.randomUUID(), "EE001", "EE002", "Test Recipient",
                Money.of(new BigDecimal("10.00"), "EUR"), "test", NameCheckResult.MATCH, null,
                NOW, NOW.plusSeconds(600));
    }
}
