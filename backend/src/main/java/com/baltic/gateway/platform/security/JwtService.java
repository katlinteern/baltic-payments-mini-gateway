package com.baltic.gateway.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long minutes,
                      Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(minutes);
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    public String issue(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(principal.id().toString())
                .claim("username", principal.username())
                .claim("role", principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().clock(() -> Date.from(clock.instant())).verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new UserPrincipal(UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class), Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
