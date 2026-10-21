package com.baltic.gateway.audit.domain;

import com.baltic.gateway.platform.security.UserPrincipal;

import java.util.UUID;

public record AuditEvent(AuditAction action, UUID userId, String username, String ipAddress, String details) {
    public static AuditEvent by(UserPrincipal actor, AuditAction action, String ipAddress, String details) {
        return new AuditEvent(action, actor.id(), actor.username(), ipAddress, details);
    }
}
