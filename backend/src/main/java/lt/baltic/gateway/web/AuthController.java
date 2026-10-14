package lt.baltic.gateway.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lt.baltic.gateway.service.AuthService;
import lt.baltic.gateway.web.Dtos.LoginRequest;
import lt.baltic.gateway.web.Dtos.LoginResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Obtain a JWT access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return auth.login(body, ClientIp.of(request));
    }
}
