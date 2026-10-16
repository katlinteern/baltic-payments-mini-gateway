package lt.baltic.gateway.identity.api;

import java.time.Instant;
import lt.baltic.gateway.platform.security.Role;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}