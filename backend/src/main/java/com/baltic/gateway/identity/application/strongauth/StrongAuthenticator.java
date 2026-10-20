package com.baltic.gateway.identity.application.strongauth;

import java.time.Instant;

/**
 * Strong customer authentication of a payment, in the shape of a mobile-ID app such as Smart-ID: the bank shows the
 * payer a short verification code, the payer checks it in the app and approves there. The bank learns the outcome
 * from the provider, never from the browser.
 */
public interface StrongAuthenticator {
    /** What the payer must compare with the app; {@code startedAt} lets a provider that keeps no state decide later. */
    record Challenge(String verificationCode, Instant startedAt) {}

    enum Outcome { PENDING, APPROVED, REJECTED }

    Challenge start(Instant now);

    Outcome outcome(Challenge challenge, Instant now);
}
