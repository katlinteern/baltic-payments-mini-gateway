package com.baltic.gateway.identity.application.login;

import java.time.Clock;
import java.util.UUID;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.identity.domain.login.User;
import com.baltic.gateway.identity.infrastructure.login.UserRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.security.JwtService;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.error.RateLimitExceededException;
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
            boolean limitReached = rateLimiter.recordFailure(ip, username);
            metrics.login("failed");
            UUID userId = user == null ? null : user.getId();
            audit.recordIndependent(new AuditEvent(AuditAction.LOGIN_FAILED, userId, username, ip, null));
            if (limitReached) audit.recordIndependent(new AuditEvent(AuditAction.LOGIN_BLOCKED, userId, username, ip, null));
            throw new BadCredentialsException("Invalid username or password");
        }
        rateLimiter.reset(ip, username);
        metrics.login("success");
        audit.recordIndependent(new AuditEvent(AuditAction.LOGIN_SUCCESS, user.getId(), user.getUsername(), ip, null));
        var principal = new UserPrincipal(user.getId(), user.getUsername());
        return new AuthResult(jwt.issue(principal), user.getUsername(), clock.instant().plus(jwt.ttl()));
    }
}
