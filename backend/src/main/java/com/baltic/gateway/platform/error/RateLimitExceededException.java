package com.baltic.gateway.platform.error;

import lombok.Getter;

public class RateLimitExceededException extends BusinessException {
    @Getter
    private final long retryAfterSeconds;

    public RateLimitExceededException(String detail, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, detail);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
