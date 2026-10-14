package lt.baltic.gateway.exceptions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
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
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException ex) { return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) { return problem(HttpStatus.CONFLICT, "Conflict", "Request conflicts with existing data"); }
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
