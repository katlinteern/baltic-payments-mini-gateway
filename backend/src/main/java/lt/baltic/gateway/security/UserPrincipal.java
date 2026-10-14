package lt.baltic.gateway.security;

import java.util.UUID;
import lt.baltic.gateway.domain.Role;

public record UserPrincipal(UUID id, String username, Role role) {
    public boolean isPrivileged() { return role == Role.ADMIN || role == Role.AUDITOR; }
}
