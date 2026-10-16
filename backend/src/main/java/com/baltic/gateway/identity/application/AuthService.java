package com.baltic.gateway.identity.application;

import java.time.Clock;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.identity.domain.User;
import com.baltic.gateway.identity.infrastructure.UserRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.security.JwtService;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.RateLimitExceededException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final LoginRateLimiter rateLimiter;
    private final Clock clock;
    private final GatewayMetrics metrics;
    // Compared against when the user is unknown so response time does not reveal which usernames exist.
    private final String decoyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                       LoginRateLimiter rateLimiter, Clock clock, GatewayMetrics metrics) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.metrics = metrics;
        this.decoyHash = encoder.encode("decoy-password-for-timing-equalisation");
    }

    public AuthResult login(String username, String password, String ip) {
        try {
            rateLimiter.assertAllowed(ip, username);
        } catch (RateLimitExceededException ex) {
            metrics.login("blocked");
            throw ex;
        }
        User user = users.findByUsername(username).orElse(null);
        boolean passwordMatches = encoder.matches(password, user == null ? decoyHash : user.getPasswordHash());
        if (user == null || !user.isEnabled() || !passwordMatches) {
            rateLimiter.recordFailure(ip, username);
            metrics.login("failed");
            audit.recordIndependent("LOGIN_FAILED", user == null ? null : user.getId(), username, ip, null);
            throw new BadCredentialsException("Invalid username or password");
        }
        rateLimiter.reset(ip, username);
        metrics.login("success");
        audit.recordIndependent("LOGIN_SUCCESS", user.getId(), user.getUsername(), ip, null);
        var principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
        return new AuthResult(jwt.issue(principal), user.getUsername(), user.getRole(), clock.instant().plus(jwt.ttl()));
    }
}
