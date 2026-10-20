package com.baltic.gateway.identity.infrastructure.strongauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Approves every authentication after a delay and verifies nothing: demo only, so it exists only when app.strong-auth.mock.enabled=true.
 */
@Component
@ConditionalOnProperty(prefix = "app.strong-auth.mock", name = "enabled", havingValue = "true")
public class MockSmartIdAuthenticator implements StrongAuthenticator {
    private final SecureRandom random = new SecureRandom();
    private final Duration delay;

    public MockSmartIdAuthenticator(@Value("${app.strong-auth.mock.delay:PT3S}") Duration delay) {
        this.delay = delay;
    }

    @Override
    public Challenge start(Instant now) {
        return new Challenge("%04d".formatted(random.nextInt(10_000)), now);
    }

    @Override
    public Outcome outcome(Challenge challenge, Instant now) {
        return now.isBefore(challenge.startedAt().plus(delay)) ? Outcome.PENDING : Outcome.APPROVED;
    }
}
