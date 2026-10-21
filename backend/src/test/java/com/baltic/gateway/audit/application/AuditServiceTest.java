package com.baltic.gateway.audit.application;

import com.baltic.gateway.audit.domain.AuditAction;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.audit.domain.AuditLog;
import com.baltic.gateway.audit.infrastructure.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    AuditLogRepository repository;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void entriesAreStampedWithTheInjectedClockAndTheRequestsCorrelationId() {
        var userId = UUID.randomUUID();
        var service = new AuditService(repository, clock);
        MDC.put("correlationId", "req-1");

        service.record(new AuditEvent(AuditAction.LOGIN_SUCCESS, userId, "alice", "127.0.0.1", "details"));

        var saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        assertEquals(NOW, saved.getValue().getCreatedAt());
        assertEquals(AuditAction.LOGIN_SUCCESS, saved.getValue().getAction());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("alice", saved.getValue().getUsername());
        assertEquals("127.0.0.1", saved.getValue().getIpAddress());
        assertEquals("details", saved.getValue().getDetails());
        assertEquals("req-1", saved.getValue().getCorrelationId());
    }
}
