package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "app.ledger.reconciliation.enabled=false",
    "spring.profiles.active=prod",
    "DB_PASSWORD=unused",
    "JWT_SECRET=seed-isolation-test-secret-with-32-chars-min"
})
@AutoConfigureMockMvc
@Testcontainers
class SeedIsolationIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("db/app-role.sql");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void defaultContextCreatesSchemaButNoDemoUsersOrAccounts() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_entries'", Integer.class));
    }

    @Test void defaultContextGrantsTheApplicationRoleItsPrivileges() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.role_table_grants "
                + "WHERE grantee = 'payments_app' AND table_name = 'ledger_entries' AND privilege_type = 'INSERT'", Integer.class));
    }

    @Test void defaultProfileDoesNotPublishTheApiDescription() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
