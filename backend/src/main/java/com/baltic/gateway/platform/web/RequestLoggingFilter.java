package com.baltic.gateway.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            logRequest(request, status, durationMs);
        }
    }

    private void logRequest(HttpServletRequest request, int status, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        log.atLevel(levelFor(status))
                .addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log("{} {} -> {} in {} ms", method, path, status, durationMs);
    }

    private static Level levelFor(int status) {
        if (status >= 500) return Level.ERROR;
        if (status >= 400) return Level.WARN;
        return Level.INFO;
    }
}
