package lt.baltic.gateway.service;

import java.time.Instant;
import lt.baltic.gateway.domain.Role;

public record AuthResult(String token, String username, Role role, Instant expiresAt) {}