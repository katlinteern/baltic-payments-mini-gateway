package lt.baltic.gateway.platform.web;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String detail, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", detail);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
