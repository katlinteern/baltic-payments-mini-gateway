package com.baltic.gateway.platform.ratelimit;

import com.baltic.gateway.platform.error.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlidingWindowRateLimiterTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, 2, Duration.ofMinutes(1));

    @Test
    void rejectsTheCallAfterTheLimitWithTheTimeToWait() {
        limiter.acquire("alice");
        now.set(now.get().plusSeconds(10));
        limiter.acquire("alice");

        var blocked = assertThrows(RateLimitExceededException.class, () -> limiter.acquire("alice"));

        assertEquals(51, blocked.getRetryAfterSeconds());
    }

    @Test
    void keysAreIndependentAndTheWindowSlides() {
        limiter.acquire("alice");
        limiter.acquire("alice");

        assertDoesNotThrow(() -> limiter.acquire("bob"));
        now.set(now.get().plusSeconds(61));
        assertDoesNotThrow(() -> limiter.acquire("alice"));
    }

    @Test
    void rejectedCallsDoNotExtendTheBlock() {
        limiter.acquire("alice");
        limiter.acquire("alice");
        for (int i = 0; i < 5; i++) assertThrows(RateLimitExceededException.class, () -> limiter.acquire("alice"));

        now.set(now.get().plusSeconds(61));

        assertDoesNotThrow(() -> limiter.acquire("alice"));
    }
}
