package com.baltic.gateway.audit.api;

import java.time.Instant;
import java.util.UUID;
import com.baltic.gateway.audit.domain.AuditLog;

public record AuditDto(Long id, String action, UUID userId, String username, String ipAddress,
                       String details, Instant createdAt) {
    public static AuditDto from(AuditLog log) {
        return new AuditDto(log.getId(), log.getAction(), log.getUserId(), log.getUsername(), log.getIpAddress(),
                log.getDetails(), log.getCreatedAt());
    }
}