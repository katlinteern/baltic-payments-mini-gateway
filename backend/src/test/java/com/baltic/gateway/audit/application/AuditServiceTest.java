package com.baltic.gateway.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.audit.domain.AuditLog;
import com.baltic.gateway.audit.infrastructure.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    @Mock AuditLogRepository repository;

    @Test void entriesAreStampedWithTheInjectedClock() {
        var userId = UUID.randomUUID();
        var service = new AuditService(repository, clock);

        service.record(new AuditEvent("LOGIN_SUCCESS", userId, "alice", "127.0.0.1", "details"));

        var saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        assertEquals(NOW, saved.getValue().getCreatedAt());
        assertEquals("LOGIN_SUCCESS", saved.getValue().getAction());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("alice", saved.getValue().getUsername());
        assertEquals("127.0.0.1", saved.getValue().getIpAddress());
        assertEquals("details", saved.getValue().getDetails());
    }
}
