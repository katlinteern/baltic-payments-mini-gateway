package lt.baltic.gateway.exceptions;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String title;
    public BusinessException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }
    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
