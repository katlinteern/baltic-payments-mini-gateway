package com.baltic.gateway.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.baltic.gateway.platform.web.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limiter for failed logins, keyed by client IP and username so one attacker cannot lock out
 * a legitimate user from elsewhere. State is in memory: with several instances, move it to a shared store.
 */
@Component
public class LoginRateLimiter {
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginRateLimiter(Clock clock,
                            @Value("${app.login-rate-limit.max-failures}") int maxFailures,
                            @Value("${app.login-rate-limit.window-minutes}") long windowMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public void assertAllowed(String ip, String username) {
        Deque<Instant> attempts = failures.get(key(ip, username));
        if (attempts == null) return;
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= maxFailures) {
                long retryAfter = Duration.between(clock.instant(), attempts.peekFirst().plus(window)).toSeconds() + 1;
                throw new RateLimitExceededException("Too many failed sign-in attempts. Try again later.", Math.max(retryAfter, 1));
            }
        }
    }

    public void recordFailure(String ip, String username) {
        Deque<Instant> attempts = failures.computeIfAbsent(key(ip, username), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
        evictIdleKeys();
    }

    public void reset(String ip, String username) {
        failures.remove(key(ip, username));
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.pollFirst();
    }

    private void evictIdleKeys() {
        if (failures.size() < 10_000) return;
        Instant cutoff = clock.instant().minus(window);
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }

    private static String key(String ip, String username) {
        return ip + '|' + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }
}
