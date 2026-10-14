package lt.baltic.gateway.service;

import java.time.Instant;
import lt.baltic.gateway.domain.User;
import lt.baltic.gateway.repo.UserRepository;
import lt.baltic.gateway.security.JwtService;
import lt.baltic.gateway.web.Dtos.LoginRequest;
import lt.baltic.gateway.web.Dtos.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }
    public LoginResponse login(LoginRequest request, String ip) {
        User user = users.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(request.password(), user.getPasswordHash())) {
            audit.recordIndependent("LOGIN_FAILED", user == null ? null : user.getId(), request.username(), ip, null);
            throw new BadCredentialsException("Invalid username or password");
        }
        audit.recordIndependent("LOGIN_SUCCESS", user.getId(), user.getUsername(), ip, null);
        return new LoginResponse(jwt.issue(user), user.getUsername(), user.getRole(), Instant.now().plus(jwt.ttl()));
    }
}
