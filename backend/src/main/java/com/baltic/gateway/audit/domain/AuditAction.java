package com.baltic.gateway.audit.domain;

/** What the audit trail records. The name is stored as is in {@code audit_log.action}, so never rename a constant. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    /** A failure limit was reached: the sign-in is refused until the window passes. Recorded once, not per refusal. */
    LOGIN_BLOCKED,
    PAYMENT_INTENT_CREATED,
    PAYMENT_AUTHENTICATION_STARTED,
    PAYMENT_AUTHENTICATION_REJECTED,
    PAYMENT_INTENT_CANCELLED,
    PAYMENT_EXECUTED,
    PAYMENT_REJECTED
}
