package com.baltic.gateway.identity.application.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import com.baltic.gateway.audit.application.AuditService;
import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.identity.domain.login.User;
import com.baltic.gateway.identity.infrastructure.login.UserRepository;
import com.baltic.gateway.platform.metrics.GatewayMetrics;
import com.baltic.gateway.platform.security.JwtService;
import com.baltic.gateway.platform.security.UserPrincipal;
import com.baltic.gateway.platform.web.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock AuditService audit;
    @Mock LoginRateLimiter rateLimiter;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private AuthService service;

    @BeforeEach void setUp() {
        when(encoder.encode(any())).thenReturn("$2a$decoy");
        service = new AuthService(users, encoder, jwt, audit, rateLimiter, CLOCK, new GatewayMetrics(registry));
    }

    private double logins(String outcome) {
        return registry.counter("auth.logins", "outcome", outcome).count();
    }

    private User user(UUID id) {
        User user = Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("$2a$hash");
        return user;
    }

    @Test void successfulLoginReturnsTokenAuditsAndResetsLimiter() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(user.getUsername()).thenReturn("alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("secret", "$2a$hash")).thenReturn(true);
        when(jwt.issue(new UserPrincipal(userId, "alice"))).thenReturn("signed-token");
        when(jwt.ttl()).thenReturn(Duration.ofMinutes(60));

        AuthResult result = service.login("alice", "secret", "127.0.0.1");

        assertEquals("signed-token", result.token());
        assertEquals("alice", result.username());
        assertEquals(CLOCK.instant().plus(Duration.ofMinutes(60)), result.expiresAt());
        verify(audit).recordIndependent(new AuditEvent(AuditAction.LOGIN_SUCCESS, userId, "alice", "127.0.0.1", null));
        verify(rateLimiter).reset("127.0.0.1", "alice");
        assertEquals(1.0, logins("success"));
    }

    @Test void badPasswordIsAuditedCountedAndDoesNotIssueToken() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login("alice", "wrong", "10.0.0.4"));

        verify(audit).recordIndependent(new AuditEvent(AuditAction.LOGIN_FAILED, userId, "alice", "10.0.0.4", null));
        verify(rateLimiter).recordFailure("10.0.0.4", "alice");
        assertEquals(1.0, logins("failed"));
        verify(jwt, never()).issue(any());
    }

    @Test void theFailureThatReachesTheLimitIsAuditedAsABlockOnce() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "$2a$hash")).thenReturn(false);
        when(rateLimiter.recordFailure("10.0.0.4", "alice")).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> service.login("alice", "wrong", "10.0.0.4"));

        verify(audit).recordIndependent(new AuditEvent(AuditAction.LOGIN_FAILED, userId, "alice", "10.0.0.4", null));
        verify(audit).recordIndependent(new AuditEvent(AuditAction.LOGIN_BLOCKED, userId, "alice", "10.0.0.4", null));
    }

    @Test void unknownUsernameStillHashesAgainstDecoyAndRevealsNothing() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        when(encoder.matches("secret", "$2a$decoy")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login("missing", "secret", "10.0.0.5"));

        verify(encoder).matches("secret", "$2a$decoy");
        verify(audit).recordIndependent(new AuditEvent(AuditAction.LOGIN_FAILED, null, "missing", "10.0.0.5", null));
        verify(jwt, never()).issue(any());
    }

    @Test void blockedClientNeverReachesCredentialCheck() {
        Mockito.doThrow(new RateLimitExceededException("blocked", 60)).when(rateLimiter).assertAllowed("10.0.0.6", "alice");

        assertThrows(RateLimitExceededException.class, () -> service.login("alice", "secret", "10.0.0.6"));

        verify(users, never()).findByUsername(any());
        assertEquals(1.0, logins("blocked"));
    }
}
