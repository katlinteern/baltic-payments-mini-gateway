package lt.baltic.gateway.dto;

import java.time.Instant;
import lt.baltic.gateway.domain.Role;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}