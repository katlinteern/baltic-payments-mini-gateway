package com.baltic.gateway.platform.web;

import com.baltic.gateway.platform.error.ErrorCode;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

public final class Problems {
    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setTitle(code.title());
        body.setProperty("code", code.name());
        addTrace(body);
        return body;
    }

    static void addTrace(ProblemDetail body) {
        body.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        body.setProperty("timestamp", Instant.now().toString());
    }
}
