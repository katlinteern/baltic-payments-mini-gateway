package lt.baltic.gateway.dtos;

import java.time.Instant;
import lt.baltic.gateway.enums.Role;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}