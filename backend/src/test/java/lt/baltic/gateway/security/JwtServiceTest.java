package lt.baltic.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;
import lt.baltic.gateway.entities.User;
import lt.baltic.gateway.enums.Role;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-with-at-least-32-bytes-long";

    @Test void issuedTokenParsesBackToPrincipal() {
        UUID id = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn("alice");
        when(user.getRole()).thenReturn(Role.USER);
        JwtService service = new JwtService(SECRET, 60);

        var principal = service.parse(service.issue(user)).orElseThrow();

        assertEquals(id, principal.id());
        assertEquals("alice", principal.username());
        assertEquals(Role.USER, principal.role());
    }

    @Test void malformedOrTamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60);
        assertTrue(service.parse("not-a-jwt").isEmpty());

        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("alice");
        when(user.getRole()).thenReturn(Role.USER);
        String token = service.issue(user);
        assertFalse(service.parse(token + "tampered").isPresent());
    }
}