package com.baltic.gateway.platform.ratelimit;

import com.baltic.gateway.platform.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class SlidingWindowRateLimiter {
    private final Clock clock;
    private final SlidingWindowCounter calls;

    public SlidingWindowRateLimiter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.calls = new SlidingWindowCounter(limit, window);
    }

    public synchronized void acquire(String key) {
        Instant now = clock.instant();
        long retryAfter = calls.retryAfterSeconds(key, now);
        if (retryAfter > 0) {
            throw new RateLimitExceededException("Too many requests. Try again later.", retryAfter);
        }
        calls.record(key, now);
    }
}
