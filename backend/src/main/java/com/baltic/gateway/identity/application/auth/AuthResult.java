package com.baltic.gateway.identity.application.auth;

import java.time.Instant;

public record AuthResult(String token, String username, Instant expiresAt) {}
