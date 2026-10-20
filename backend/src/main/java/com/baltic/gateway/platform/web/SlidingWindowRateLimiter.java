package com.baltic.gateway.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allows at most {@code limit} calls per key within a sliding window. Used to slow down endpoints that act as
 * oracles (payee name checks) or are expensive to abuse. State is in memory and bounded: the least recently used
 * keys are evicted. With several instances the effective limit is per instance; move it to a shared store then.
 */
public class SlidingWindowRateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> calls = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    public SlidingWindowRateLimiter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.limit = limit;
        this.window = window;
    }

    /** Records a call for the key, or throws {@link RateLimitExceededException} with the time to wait. */
    public synchronized void acquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> timestamps = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant cutoff = now.minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) timestamps.pollFirst();
        if (timestamps.size() >= limit) {
            long retryAfter = Math.max(Duration.between(now, timestamps.peekFirst().plus(window)).toSeconds() + 1, 1);
            throw new RateLimitExceededException("Too many requests. Try again later.", retryAfter);
        }
        timestamps.addLast(now);
    }
}
