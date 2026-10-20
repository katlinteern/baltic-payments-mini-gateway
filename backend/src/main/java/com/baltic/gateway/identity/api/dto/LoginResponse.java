package com.baltic.gateway.identity.api.dto;

import java.time.Instant;

public record LoginResponse(String token, String username, Instant expiresAt) {}