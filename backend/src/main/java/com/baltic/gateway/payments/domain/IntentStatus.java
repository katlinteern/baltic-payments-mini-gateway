package com.baltic.gateway.payments.domain;

/** Lifecycle of a payment intent: reviewed, authenticated, then executed (or ended without moving money). */
public enum IntentStatus {
    /** Prepared and name-checked; waiting for the payer to confirm. */
    READY,
    /** The payer has been asked to approve it in the authentication app. */
    AWAITING_AUTHENTICATION,
    COMPLETED,
    /** Authentication was rejected, or the payment itself was refused (for example insufficient funds). */
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isOpen() {
        return this == READY || this == AWAITING_AUTHENTICATION;
    }
}
