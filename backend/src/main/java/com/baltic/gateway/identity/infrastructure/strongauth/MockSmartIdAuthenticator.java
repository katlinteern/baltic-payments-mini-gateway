package com.baltic.gateway.identity.infrastructure.strongauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in for Smart-ID that approves every authentication after a short delay, so the payment flow can be
 * demonstrated without a real provider. It verifies nothing: it must never run outside a demo, which is why it exists
 * only when {@code app.strong-auth.mock.enabled=true} (set by the {@code local} profile). Without any authenticator
 * the application starts, but payments cannot be confirmed.
 *
 * <p>It keeps no state: a challenge is approved once its delay has passed, so it also works after a restart.
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
