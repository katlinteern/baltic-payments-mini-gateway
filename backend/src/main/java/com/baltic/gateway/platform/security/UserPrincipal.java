package com.baltic.gateway.platform.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String username, Role role) {
    public boolean isPrivileged() { return role == Role.ADMIN || role == Role.AUDITOR; }
}
