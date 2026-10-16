package com.baltic.gateway.platform.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("correlationId", MDC.get("correlationId"));
        body.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException ex) { return problem(ex.getStatus(), ex.getTitle(), ex.getMessage()); }
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
        ResponseEntity<ProblemDetail> base = problem(ex.getStatus(), ex.getTitle(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(base.getBody());
    }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException ex) { return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) { return problem(HttpStatus.CONFLICT, "Conflict", "Request conflicts with existing data"); }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockContention(PessimisticLockingFailureException ex) {
        logger.warn("Row lock not acquired in time", ex);
        ResponseEntity<ProblemDetail> base = problem(HttpStatus.CONFLICT, "Account busy",
                "The account is being used by another request. Retry the payment with the same Idempotency-Key.");
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1").body(base.getBody());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) { logger.error("Unhandled request error", ex); return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error"); }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "One or more fields are invalid");
        body.setTitle("Validation failed");
        body.setProperty("errors", errors);
        body.setProperty("correlationId", MDC.get("correlationId"));
        body.setProperty("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, headers, status);
    }
}
