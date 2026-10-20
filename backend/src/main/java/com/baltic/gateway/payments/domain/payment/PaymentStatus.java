package com.baltic.gateway.payments.domain.payment;

public enum PaymentStatus {
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
