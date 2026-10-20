package com.baltic.gateway.platform.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String username) {}
