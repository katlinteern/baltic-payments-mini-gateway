package lt.baltic.gateway.services;

import lt.baltic.gateway.entities.User;
import lt.baltic.gateway.repositories.UserRepository;
import lt.baltic.gateway.security.JwtService;
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
    public AuthResult login(String username, String password, String ip) {
        User user = users.findByUsername(username).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(password, user.getPasswordHash())) {
            audit.recordIndependent("LOGIN_FAILED", user == null ? null : user.getId(), username, ip, null);
            throw new BadCredentialsException("Invalid username or password");
        }
        audit.recordIndependent("LOGIN_SUCCESS", user.getId(), user.getUsername(), ip, null);
        return new AuthResult(jwt.issue(user), user.getUsername(), user.getRole(), java.time.Instant.now().plus(jwt.ttl()));
    }
}
