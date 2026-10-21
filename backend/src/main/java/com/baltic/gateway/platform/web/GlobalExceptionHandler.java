package com.baltic.gateway.platform.web;

import com.baltic.gateway.platform.error.BusinessException;
import com.baltic.gateway.platform.error.ErrorCode;
import com.baltic.gateway.platform.error.RateLimitExceededException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status()).body(Problems.of(code, detail));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException ex) {
        return problem(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Problems.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException ex) {
        return problem(ErrorCode.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        logger.warn("Data integrity violation: " + ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockContention(PessimisticLockingFailureException ex) {
        logger.warn("Row lock not acquired in time", ex);
        return ResponseEntity.status(ErrorCode.ACCOUNT_BUSY.status())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(Problems.of(
                        ErrorCode.ACCOUNT_BUSY,
                        "The account is being used by another request. Try again in a moment."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        logger.error("Unhandled request error", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = Problems.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return new ResponseEntity<>(body, headers, ErrorCode.VALIDATION_FAILED.status());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", status == null ? "ERROR" : status.name());
            Problems.addTrace(problem);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults()
                .forEach(result -> result.getResolvableErrors().stream()
                        .findFirst()
                        .ifPresent(error -> errors.putIfAbsent(
                                String.valueOf(result.getMethodParameter().getParameterName()),
                                error.getDefaultMessage())));
        ProblemDetail body = Problems.of(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid");
        body.setProperty("errors", errors);
        return new ResponseEntity<>(body, headers, ErrorCode.VALIDATION_FAILED.status());
    }
}
