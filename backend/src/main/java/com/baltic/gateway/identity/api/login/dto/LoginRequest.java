package com.baltic.gateway.identity.api.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank(message = "Username is required") @Size(max = 64, message = "Username must be at most 64 characters") String username,
                           @NotBlank(message = "Password is required") @Size(max = 128, message = "Password must be at most 128 characters") String password) {}