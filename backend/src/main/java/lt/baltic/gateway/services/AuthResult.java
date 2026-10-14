package lt.baltic.gateway.services;

import java.time.Instant;
import lt.baltic.gateway.enums.Role;

public record AuthResult(String token, String username, Role role, Instant expiresAt) {}