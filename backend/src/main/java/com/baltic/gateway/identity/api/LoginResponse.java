package com.baltic.gateway.identity.api;

import java.time.Instant;
import com.baltic.gateway.platform.security.Role;

public record LoginResponse(String token, String username, Role role, Instant expiresAt) {}