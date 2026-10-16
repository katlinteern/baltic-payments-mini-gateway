package com.baltic.gateway.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-with-at-least-32-bytes-long";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private final UserPrincipal alice = new UserPrincipal(UUID.randomUUID(), "alice", Role.USER);

    @Test void issuedTokenParsesBackToPrincipal() {
        JwtService service = new JwtService(SECRET, 60, NOW);

        var principal = service.parse(service.issue(alice)).orElseThrow();

        assertEquals(alice, principal);
    }

    @Test void malformedOrTamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60, NOW);

        assertTrue(service.parse("not-a-jwt").isEmpty());
        assertFalse(service.parse(service.issue(alice) + "tampered").isPresent());
    }

    @Test void tokenSignedWithAnotherKeyIsRejected() {
        var other = new JwtService("another-secret-with-at-least-32-bytes-long!!", 60, NOW);

        assertTrue(new JwtService(SECRET, 60, NOW).parse(other.issue(alice)).isEmpty());
    }

    @Test void expiredTokenIsRejected() {
        String token = new JwtService(SECRET, 60, NOW).issue(alice);
        Clock later = Clock.offset(NOW, Duration.ofMinutes(61));

        assertTrue(new JwtService(SECRET, 60, later).parse(token).isEmpty());
    }
}
