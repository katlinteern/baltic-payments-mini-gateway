package com.baltic.gateway.identity.application.strongauth;

import java.time.Instant;

public interface StrongAuthenticator {
    record Challenge(String verificationCode, Instant startedAt) {}

    enum Outcome {
        PENDING,
        APPROVED,
        REJECTED
    }

    Challenge start(Instant now);

    Outcome outcome(Challenge challenge, Instant now);
}
