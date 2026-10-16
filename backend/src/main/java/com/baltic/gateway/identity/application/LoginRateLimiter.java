package com.baltic.gateway.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.baltic.gateway.platform.web.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limiter for failed logins. Three independent windows are checked so that no single dimension
 * can be used to get around the limit:
 * <ul>
 *   <li>IP + username: stops one client from guessing one account, without locking the owner out from elsewhere;</li>
 *   <li>IP: stops password spraying, where one client tries many usernames;</li>
 *   <li>username: stops a distributed attack on one account from many addresses.</li>
 * </ul>
 * A successful login clears only the IP + username window; the other two keep counting, otherwise an attacker
 * holding one valid account could reset them at will. The per-username limit means an attacker can lock a known
 * user out for the window, which is the usual trade-off; keep it higher than the per-pair limit. State is in
 * memory and bounded (least recently used keys are evicted); with several instances, move it to a shared store.
 */
@Component
public class LoginRateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Duration window;
    private final FailureWindow perPair;
    private final FailureWindow perIp;
    private final FailureWindow perUsername;

    public LoginRateLimiter(Clock clock,
                            @Value("${app.login-rate-limit.max-failures}") int maxFailuresPerPair,
                            @Value("${app.login-rate-limit.max-failures-per-ip}") int maxFailuresPerIp,
                            @Value("${app.login-rate-limit.max-failures-per-username}") int maxFailuresPerUsername,
                            @Value("${app.login-rate-limit.window-minutes}") long windowMinutes) {
        this.clock = clock;
        this.window = Duration.ofMinutes(windowMinutes);
        this.perPair = new FailureWindow(maxFailuresPerPair);
        this.perIp = new FailureWindow(maxFailuresPerIp);
        this.perUsername = new FailureWindow(maxFailuresPerUsername);
    }

    public synchronized void assertAllowed(String ip, String username) {
        Instant now = clock.instant();
        long retryAfter = Math.max(perPair.retryAfterSeconds(pairKey(ip, username), now),
                Math.max(perIp.retryAfterSeconds(ip, now), perUsername.retryAfterSeconds(normalize(username), now)));
        if (retryAfter > 0) {
            throw new RateLimitExceededException("Too many failed sign-in attempts. Try again later.", retryAfter);
        }
    }

    public synchronized void recordFailure(String ip, String username) {
        Instant now = clock.instant();
        perPair.record(pairKey(ip, username), now);
        perIp.record(ip, now);
        perUsername.record(normalize(username), now);
    }

    public synchronized void reset(String ip, String username) {
        perPair.clear(pairKey(ip, username));
    }

    private static String pairKey(String ip, String username) {
        return ip + '|' + normalize(username);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    /** Failure timestamps per key in an LRU map, so memory stays bounded even when every request uses a new key. */
    private final class FailureWindow {
        private final int limit;
        private final Map<String, Deque<Instant>> failures = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > MAX_TRACKED_KEYS;
            }
        };

        private FailureWindow(int limit) {
            this.limit = limit;
        }

        /** Seconds until the key may try again, or 0 when it is not blocked. */
        long retryAfterSeconds(String key, Instant now) {
            Deque<Instant> attempts = failures.get(key);
            if (attempts == null) return 0;
            prune(attempts, now);
            if (attempts.size() < limit) return 0;
            return Math.max(Duration.between(now, attempts.peekFirst().plus(window)).toSeconds() + 1, 1);
        }

        void record(String key, Instant now) {
            Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
            prune(attempts, now);
            attempts.addLast(now);
        }

        void clear(String key) {
            failures.remove(key);
        }

        private void prune(Deque<Instant> attempts, Instant now) {
            Instant cutoff = now.minus(window);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.pollFirst();
        }
    }
}
