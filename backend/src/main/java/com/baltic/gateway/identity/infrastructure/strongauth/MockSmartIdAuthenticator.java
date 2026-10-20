package com.baltic.gateway.identity.infrastructure.strongauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator.Outcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.strong-auth.mock", name = "enabled", havingValue = "true")
public class MockSmartIdAuthenticator implements StrongAuthenticator {
    private final SecureRandom random = new SecureRandom();
    private final Duration delay;
    private final Outcome configuredOutcome;

    public MockSmartIdAuthenticator(@Value("${app.strong-auth.mock.delay:PT3S}") Duration delay,
                                    @Value("${app.strong-auth.mock.outcome:APPROVED}") Outcome configuredOutcome) {
        if (configuredOutcome == Outcome.PENDING) {
            throw new IllegalArgumentException("The configured mock outcome must be APPROVED or REJECTED");
        }
        this.delay = delay;
        this.configuredOutcome = configuredOutcome;
    }

    @Override
    public Challenge start(Instant now) {
        return new Challenge("%04d".formatted(random.nextInt(10_000)), now);
    }

    @Override
    public Outcome outcome(Challenge challenge, Instant now) {
        return now.isBefore(challenge.startedAt().plus(delay)) ? Outcome.PENDING : configuredOutcome;
    }
}
