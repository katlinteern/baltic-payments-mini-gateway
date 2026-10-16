package com.baltic.gateway.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void lockTimeoutBecomesARetryableConflictInsteadOfAServerError() {
        var response = handler.lockContention(new CannotAcquireLockException("could not obtain lock"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Account busy", response.getBody().getTitle());
    }
}
