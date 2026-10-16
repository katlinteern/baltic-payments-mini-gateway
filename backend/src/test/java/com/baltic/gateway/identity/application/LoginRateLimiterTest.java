package lt.baltic.gateway.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import lt.baltic.gateway.platform.web.RateLimitExceededException;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 3, 15);

    @Test void blocksAfterMaxFailuresWithRetryAfter() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("10.0.0.1", "alice");

        var blocked = assertThrows(RateLimitExceededException.class, () -> limiter.assertAllowed("10.0.0.1", "alice"));

        assertEquals(15 * 60 + 1, blocked.getRetryAfterSeconds());
    }

    @Test void failuresAreScopedPerIpAndUsernameCaseInsensitively() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("10.0.0.1", "Alice");

        assertThrows(RateLimitExceededException.class, () -> limiter.assertAllowed("10.0.0.1", "alice"));
        assertDoesNotThrow(() -> limiter.assertAllowed("10.0.0.2", "alice"));
        assertDoesNotThrow(() -> limiter.assertAllowed("10.0.0.1", "bob"));
    }

    @Test void windowExpiresAndSuccessResetsCounter() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("10.0.0.1", "alice");
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertDoesNotThrow(() -> limiter.assertAllowed("10.0.0.1", "alice"));

        for (int i = 0; i < 3; i++) limiter.recordFailure("10.0.0.1", "alice");
        limiter.reset("10.0.0.1", "alice");
        assertDoesNotThrow(() -> limiter.assertAllowed("10.0.0.1", "alice"));
    }
}
