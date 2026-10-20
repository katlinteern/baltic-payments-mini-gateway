package com.baltic.gateway.platform.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Invalid amount"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "Invalid transfer"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    ACCOUNT_BUSY(HttpStatus.CONFLICT, "Account busy"),
    PAYMENT_STATE_CONFLICT(HttpStatus.CONFLICT, "Payment state conflict"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    BENEFICIARY_NAME_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Beneficiary name mismatch"),
    IDEMPOTENCY_KEY_REUSE(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    STRONG_AUTHENTICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Strong authentication unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    /** A business rule was broken, as opposed to a malformed request: worth a place in the audit trail. */
    public boolean isRuleViolation() {
        return status == HttpStatus.FORBIDDEN || status == HttpStatus.NOT_FOUND
                || status == HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String metricReason() {
        return name().toLowerCase(Locale.ROOT);
    }
}
