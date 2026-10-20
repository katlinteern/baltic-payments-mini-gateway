package com.baltic.gateway.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Allows at most {@code limit} calls per key within a sliding window. Used to slow down endpoints that act as
 * oracles (payee name checks) or are expensive to abuse. State is in memory and bounded: the least recently used
 * keys are evicted. With several instances the effective limit is per instance; move it to a shared store then.
 */
public class SlidingWindowRateLimiter {
    private final Clock clock;
    private final SlidingWindowCounter calls;

    public SlidingWindowRateLimiter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.calls = new SlidingWindowCounter(limit, window);
    }

    /** Records a call for the key, or throws {@link RateLimitExceededException} with the time to wait. */
    public synchronized void acquire(String key) {
        Instant now = clock.instant();
        long retryAfter = calls.retryAfterSeconds(key, now);
        if (retryAfter > 0) {
            throw new RateLimitExceededException("Too many requests. Try again later.", retryAfter);
        }
        calls.record(key, now);
    }
}
