package lt.baltic.gateway.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lt.baltic.gateway.entities.User;
import lt.baltic.gateway.enums.Role;
import lt.baltic.gateway.repositories.UserRepository;
import lt.baltic.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock AuditService audit;
    @InjectMocks AuthService service;

    @Test void successfulLoginReturnsTokenAndAudits() {
        UUID userId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("alice");
        when(user.getRole()).thenReturn(Role.USER);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("$2a$hash");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("secret", "$2a$hash")).thenReturn(true);
        when(jwt.issue(user)).thenReturn("signed-token");
        when(jwt.ttl()).thenReturn(Duration.ofMinutes(60));

        AuthResult result = service.login("alice", "secret", "127.0.0.1");

        assertEquals("signed-token", result.token());
        assertEquals("alice", result.username());
        assertEquals(Role.USER, result.role());
        verify(audit).recordIndependent("LOGIN_SUCCESS", userId, "alice", "127.0.0.1", null);
    }

    @Test void badPasswordIsAuditedAndDoesNotIssueToken() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.isEnabled()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("$2a$hash");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login("alice", "wrong", "10.0.0.4"));

        verify(audit).recordIndependent("LOGIN_FAILED", user.getId(), "alice", "10.0.0.4", null);
        verify(jwt, never()).issue(user);
    }

    @Test void unknownUsernameIsAuditedWithoutRevealingIt() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.login("missing", "secret", "10.0.0.5"));

        verify(audit).recordIndependent("LOGIN_FAILED", null, "missing", "10.0.0.5", null);
        verify(jwt, never()).issue(org.mockito.ArgumentMatchers.any());
    }
}