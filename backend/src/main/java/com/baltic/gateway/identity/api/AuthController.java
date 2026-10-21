package com.baltic.gateway.identity.api;

import com.baltic.gateway.identity.application.auth.AuthResult;
import com.baltic.gateway.identity.application.auth.AuthService;
import com.baltic.gateway.platform.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Obtain a JWT access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AuthResult result = auth.login(body.username(), body.password(), ClientIp.of(request));
        return new LoginResponse(result.token(), result.username(), result.expiresAt());
    }
}
