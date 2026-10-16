package com.baltic.gateway.identity.application;

import java.time.Instant;
import com.baltic.gateway.platform.security.Role;

public record AuthResult(String token, String username, Role role, Instant expiresAt) {}