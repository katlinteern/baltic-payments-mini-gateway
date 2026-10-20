package com.baltic.gateway.audit.domain;

/** Stored by name in audit_log.action: never rename a constant. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    /** Recorded once when the limit is reached, not for every refusal after it. */
    LOGIN_BLOCKED,
    PAYMENT_INTENT_CREATED,
    PAYMENT_AUTHENTICATION_STARTED,
    PAYMENT_AUTHENTICATION_REJECTED,
    PAYMENT_INTENT_CANCELLED,
    PAYMENT_EXECUTED,
    PAYMENT_REJECTED
}
