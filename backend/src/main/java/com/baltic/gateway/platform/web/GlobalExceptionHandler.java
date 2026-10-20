package com.baltic.gateway.platform.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
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
    /** RFC 7807 body with the stable {@code code}, the correlation id and a timestamp. */
    public static ProblemDetail problemDetail(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setTitle(code.title());
        body.setProperty("code", code.name());
        body.setProperty("correlationId", MDC.get("correlationId"));
        body.setProperty("timestamp", Instant.now().toString());
        return body;
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status()).body(problemDetail(code, detail));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException ex) {
        return problem(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problemDetail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException ex) {
        return problem(ErrorCode.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        return problem(ErrorCode.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockContention(PessimisticLockingFailureException ex) {
        logger.warn("Row lock not acquired in time", ex);
        return ResponseEntity.status(ErrorCode.ACCOUNT_BUSY.status())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problemDetail(ErrorCode.ACCOUNT_BUSY,
                        "The account is being used by another request. Try again in a moment."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        logger.error("Unhandled request error", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = problemDetail(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return new ResponseEntity<>(body, headers, ErrorCode.VALIDATION_FAILED.status());
    }
}
