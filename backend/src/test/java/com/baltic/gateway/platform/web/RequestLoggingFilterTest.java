package com.baltic.gateway.platform.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void successfulRequestIsLoggedAtInfoWithMethodPathStatusAndDuration() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/payments");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(201));

        var event = single();
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().startsWith("POST /api/payments -> 201 in "));
        var fields = keyValues(event);
        assertEquals("POST", fields.get("method"));
        assertEquals("/api/payments", fields.get("path"));
        assertEquals(201, fields.get("status"));
        assertTrue(fields.get("durationMs") instanceof Long);
    }

    @Test
    void clientErrorIsLoggedAtWarn() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/accounts"),
                new MockHttpServletResponse(),
                (req, res) -> ((MockHttpServletResponse) res).setStatus(403));

        assertEquals(Level.WARN, single().getLevel());
    }

    @Test
    void serverErrorIsLoggedAtError() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/accounts"),
                new MockHttpServletResponse(),
                (req, res) -> ((MockHttpServletResponse) res).setStatus(503));

        assertEquals(Level.ERROR, single().getLevel());
    }

    @Test
    void escapedExceptionIsLoggedAsServerErrorAndStillPropagates() {
        assertThrows(
                ServletException.class,
                () -> filter.doFilter(
                        new MockHttpServletRequest("GET", "/api/accounts"),
                        new MockHttpServletResponse(),
                        (req, res) -> {
                            throw new ServletException("boom");
                        }));

        var event = single();
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals(500, keyValues(event).get("status"));
    }

    @Test
    void queryStringIsNeverLogged() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/payments");
        request.setQueryString("token=secret");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        var event = single();
        assertFalse(event.getFormattedMessage().contains("secret"));
        assertFalse(keyValues(event).values().stream()
                .anyMatch(v -> String.valueOf(v).contains("secret")));
    }

    private ILoggingEvent single() {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }
}
