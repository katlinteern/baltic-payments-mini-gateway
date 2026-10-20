package com.baltic.gateway.payments.domain.intent;

public enum IntentStatus {
    READY,
    AWAITING_AUTHENTICATION,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isOpen() {
        return this == READY || this == AWAITING_AUTHENTICATION;
    }
}
