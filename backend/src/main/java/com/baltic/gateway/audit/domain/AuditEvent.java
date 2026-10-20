package com.baltic.gateway.audit.domain;

import java.util.UUID;
import com.baltic.gateway.platform.security.UserPrincipal;

/** Something worth recording: what happened, who did it, from where, and free-text details. */
public record AuditEvent(AuditAction action, UUID userId, String username, String ipAddress, String details) {
    public static AuditEvent by(UserPrincipal actor, AuditAction action, String ipAddress, String details) {
        return new AuditEvent(action, actor.id(), actor.username(), ipAddress, details);
    }
}
