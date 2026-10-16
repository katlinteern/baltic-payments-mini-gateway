package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import com.baltic.gateway.payments.application.PaymentCommand;
import com.baltic.gateway.payments.application.PaymentService;
import com.baltic.gateway.platform.money.Money;
import com.baltic.gateway.platform.security.Role;
import com.baltic.gateway.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The application runs as the unprivileged role payments_app while Liquibase runs as the schema owner, exactly as in
 * Docker Compose. Proves that payments work with those rights and that the ledger and audit log cannot be rewritten.
 */
@SpringBootTest(properties = "app.ledger.reconciliation.enabled=false")
@Testcontainers
@ActiveProfiles("local")
class RestrictedDatabaseRoleIntegrationTest {
    private static final String APP_ROLE = "payments_app";
    private static final String APP_PASSWORD = "app-test-password";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("db/app-role.sql");

    @DynamicPropertySource
    static void connectAsRestrictedRole(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
    }

    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;

    @Test void theApplicationConnectsAsTheRestrictedRole() {
        assertEquals(APP_ROLE, jdbc.queryForObject("SELECT current_user", String.class));
    }

    @Test void paymentsWorkWithTheRestrictedRole() {
        var alice = new UserPrincipal(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "alice", Role.USER);

        var result = payments.execute(alice, new PaymentCommand("LT121000011101001000", "EE382200221020145685", "Bob Tamm",
                Money.of(new BigDecimal("1.00"), "EUR"), "restricted-role"), "restricted-key-001", "127.0.0.1");

        assertFalse(result.replay());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?", Integer.class, result.transaction().getId()));
    }

    @Test void theRestrictedRoleCannotRewriteRemoveOrDisarmTheLedgerAndAuditLog() throws Exception {
        List<String> forbidden = List.of(
                "UPDATE ledger_entries SET amount = amount",
                "DELETE FROM ledger_entries",
                "TRUNCATE ledger_entries",
                "UPDATE audit_log SET details = NULL",
                "DELETE FROM audit_log",
                "TRUNCATE audit_log",
                "UPDATE transactions SET amount = amount",
                "DELETE FROM accounts",
                "UPDATE users SET role = 'AUDITOR'",
                "ALTER TABLE ledger_entries DISABLE TRIGGER ALL",
                "DROP TRIGGER trg_ledger_entries_immutable ON ledger_entries");

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_ROLE, APP_PASSWORD);
             var statement = connection.createStatement()) {
            for (String sql : forbidden) {
                var failure = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
                assertTrue(failure.getMessage().matches("(?s).*(permission denied|must be owner).*"), sql + " -> " + failure.getMessage());
            }
        }
    }
}
