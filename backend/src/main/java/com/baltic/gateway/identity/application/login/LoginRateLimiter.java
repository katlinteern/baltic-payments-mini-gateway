package com.baltic.gateway.identity.application.login;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import com.baltic.gateway.platform.web.RateLimitExceededException;
import com.baltic.gateway.platform.web.SlidingWindowCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Three independent windows, so no single dimension gets around the limit: IP + username (one client guessing one
 * account), IP (password spraying) and username (a distributed attack on one account). A successful login clears
 * only the IP + username window, or an attacker with one valid account could reset the others. In memory, so per instance.
 */
@Component
public class LoginRateLimiter {
    private final Clock clock;
    private final SlidingWindowCounter perPair;
    private final SlidingWindowCounter perIp;
    private final SlidingWindowCounter perUsername;

    public LoginRateLimiter(Clock clock,
                            @Value("${app.login-rate-limit.max-failures}") int maxFailuresPerPair,
                            @Value("${app.login-rate-limit.max-failures-per-ip}") int maxFailuresPerIp,
                            @Value("${app.login-rate-limit.max-failures-per-username}") int maxFailuresPerUsername,
                            @Value("${app.login-rate-limit.window-minutes}") long windowMinutes) {
        this.clock = clock;
        Duration window = Duration.ofMinutes(windowMinutes);
        this.perPair = new SlidingWindowCounter(maxFailuresPerPair, window);
        this.perIp = new SlidingWindowCounter(maxFailuresPerIp, window);
        this.perUsername = new SlidingWindowCounter(maxFailuresPerUsername, window);
    }

    public synchronized void assertAllowed(String ip, String username) {
        Instant now = clock.instant();
        long retryAfter = Math.max(perPair.retryAfterSeconds(pairKey(ip, username), now),
                Math.max(perIp.retryAfterSeconds(ip, now), perUsername.retryAfterSeconds(normalize(username), now)));
        if (retryAfter > 0) {
            throw new RateLimitExceededException("Too many failed sign-in attempts. Try again later.", retryAfter);
        }
    }

    public synchronized boolean recordFailure(String ip, String username) {
        Instant now = clock.instant();
        boolean pairReached = perPair.record(pairKey(ip, username), now);
        boolean ipReached = perIp.record(ip, now);
        boolean usernameReached = perUsername.record(normalize(username), now);
        return pairReached || ipReached || usernameReached;
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
}
