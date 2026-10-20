package com.baltic.gateway.identity.infrastructure.strongauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator.Outcome;
import org.junit.jupiter.api.Test;

class MockSmartIdAuthenticatorTest {
    private static final Instant START = Instant.parse("2026-10-19T10:00:00Z");
    private final MockSmartIdAuthenticator authenticator = new MockSmartIdAuthenticator(Duration.ofSeconds(3));

    @Test
    void showsAFourDigitCodeAndApprovesOnlyOnceTheDelayHasPassed() {
        var challenge = authenticator.start(START);

        assertTrue(challenge.verificationCode().matches("[0-9]{4}"), challenge.verificationCode());
        assertEquals(Outcome.PENDING, authenticator.outcome(challenge, START.plusSeconds(2)));
        assertEquals(Outcome.APPROVED, authenticator.outcome(challenge, START.plusSeconds(3)));
    }

    @Test
    void keepsNoStateSoAnApprovalSurvivesARestart() {
        var challenge = new MockSmartIdAuthenticator(Duration.ofSeconds(3)).start(START);

        assertEquals(Outcome.APPROVED, new MockSmartIdAuthenticator(Duration.ofSeconds(3))
                .outcome(challenge, START.plusSeconds(10)));
    }
}
