package com.baltic.gateway.platform.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    @Getter
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }

    public String getTitle() {
        return code.title();
    }
}
