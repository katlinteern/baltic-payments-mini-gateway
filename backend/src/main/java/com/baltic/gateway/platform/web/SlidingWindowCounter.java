package com.baltic.gateway.platform.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Timestamps of events per key within a sliding window, blocking a key once it has {@code limit} events in the window.
 * Memory is bounded: the least recently used keys are evicted, so it stays small even when every request uses a new
 * key. Not thread-safe; the owner synchronises and decides when to record (every call, or only failures).
 */
public class SlidingWindowCounter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> events = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    public SlidingWindowCounter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /** Seconds until the key may act again, or 0 when it is not blocked. */
    public long retryAfterSeconds(String key, Instant now) {
        Deque<Instant> timestamps = events.get(key);
        if (timestamps == null) return 0;
        prune(timestamps, now);
        if (timestamps.size() < limit) return 0;
        return Math.max(Duration.between(now, timestamps.peekFirst().plus(window)).toSeconds() + 1, 1);
    }

    /** Records an event; true when this one made the key reach its limit, so it is blocked from now on. */
    public boolean record(String key, Instant now) {
        Deque<Instant> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(timestamps, now);
        timestamps.addLast(now);
        return timestamps.size() == limit;
    }

    public void clear(String key) {
        events.remove(key);
    }

    private void prune(Deque<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) timestamps.pollFirst();
    }
}
