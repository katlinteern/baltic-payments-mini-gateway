package com.baltic.gateway.platform.web;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers. They are returned as the {@code code} property of every problem
 * response, so clients do not have to match on titles, and they are the {@code reason} tag of rejection metrics.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Invalid amount"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "Invalid transfer"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    ACCOUNT_BUSY(HttpStatus.CONFLICT, "Account busy"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    BENEFICIARY_NAME_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Beneficiary name mismatch"),
    IDEMPOTENCY_KEY_REUSE(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Low-cardinality label for metrics, for example {@code insufficient_funds}. */
    public String metricReason() {
        return name().toLowerCase(Locale.ROOT);
    }
}
