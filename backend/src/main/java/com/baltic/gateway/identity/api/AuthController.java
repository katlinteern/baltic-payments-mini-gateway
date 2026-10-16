package lt.baltic.gateway.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lt.baltic.gateway.identity.application.AuthResult;
import lt.baltic.gateway.identity.application.AuthService;
import lt.baltic.gateway.platform.web.ClientIp;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return new LoginResponse(result.token(), result.username(), result.role(), result.expiresAt());
    }
}
