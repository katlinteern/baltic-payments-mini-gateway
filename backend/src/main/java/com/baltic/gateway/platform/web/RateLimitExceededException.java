package com.baltic.gateway.platform.web;

public class RateLimitExceededException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String detail, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, detail);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
