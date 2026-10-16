package lt.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guards against demo credentials leaking into environments that run with the default (prod) Liquibase context.
 * The profile is pinned explicitly because CI exports SPRING_PROFILES_ACTIVE=local for the other tests.
 */
@SpringBootTest(properties = {
    "spring.profiles.active=prod",
    "DB_PASSWORD=unused",
    "JWT_SECRET=seed-isolation-test-secret-with-32-chars-min"
})
@Testcontainers
class SeedIsolationIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired JdbcTemplate jdbc;

    @Test void defaultContextCreatesSchemaButNoDemoUsersOrAccounts() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_entries'", Integer.class));
    }
}
