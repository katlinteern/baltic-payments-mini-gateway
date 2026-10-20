package com.baltic.gateway.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * In memory and bounded (least recently used keys are evicted), so with several instances the limit is per instance.
 */
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
