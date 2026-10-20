package com.baltic.gateway.identity.api.login.dto;

import java.time.Instant;

public record LoginResponse(String token, String username, Instant expiresAt) {}