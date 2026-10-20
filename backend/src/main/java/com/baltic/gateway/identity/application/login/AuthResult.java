package com.baltic.gateway.identity.application.login;

import java.time.Instant;

public record AuthResult(String token, String username, Instant expiresAt) {}