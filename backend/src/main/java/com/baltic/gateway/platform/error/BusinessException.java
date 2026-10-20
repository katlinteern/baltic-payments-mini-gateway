package com.baltic.gateway.platform.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() { return code; }
    public HttpStatus getStatus() { return code.status(); }
    public String getTitle() { return code.title(); }
}
