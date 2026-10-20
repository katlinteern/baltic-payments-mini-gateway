package com.baltic.gateway.identity.application.strongauth;

import java.time.Instant;

/**
 * The payer compares a verification code with a mobile-ID app and approves there; the outcome comes from the provider, never the browser.
 */
public interface StrongAuthenticator {
    record Challenge(String verificationCode, Instant startedAt) {}

    enum Outcome { PENDING, APPROVED, REJECTED }

    Challenge start(Instant now);

    Outcome outcome(Challenge challenge, Instant now);
}
