package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baltic.gateway.accounts.application.ledger.LedgerReconciliationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.ledger.reconciliation.enabled=false", "app.strong-auth.mock.delay=PT0S"})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class GatewayApiIntegrationTest {
    private static final String ALICE_IBAN = PaymentFlow.ALICE_IBAN;
    private static final String ALICE_SECOND_IBAN = "LV80BANK0000435195001";
    private static final String BOB_IBAN = PaymentFlow.BOB_IBAN;
    private static final String ALICE_ACCOUNT_ID = "10000000-0000-0000-0000-000000000001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerReconciliationService reconciliation;
    PaymentFlow flow;

    @BeforeEach
    void resetPaymentFixtures() {
        flow = new PaymentFlow(mvc, mapper);
        jdbc.execute("TRUNCATE payments, ledger_entries, transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("500.00"), ALICE_SECOND_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
    }

    @Test
    void frameworkErrorsCarryTheSameCodeAndCorrelationIdAsBusinessErrors() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        mvc.perform(get("/api/payments/not-a-uuid").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/transactions").param("q", "a".repeat(121)).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.q").isNotEmpty());
    }

    @Test
    void anAuthenticatedUserSeesOnlyOwnAccountsAndNoBackOfficeEndpointsAreExposed() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        String aliceToken = login("alice", "Alice123!");
        mvc.perform(get("/api/accounts").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/audit").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/{id}/ledger", ALICE_ACCOUNT_ID).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paymentBooksBalancedLedgerEntriesThatMatchAccountBalances() throws Exception {
        String token = login("alice", "Alice123!");

        JsonNode completed = flow.pay(token, PaymentFlow.aliceToBob("25.50", "ledger"));
        org.junit.jupiter.api.Assertions.assertEquals("MATCH", completed.get("nameCheck").asText());

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END) FROM ledger_entries", BigDecimal.class)));
        assertEquals(0, balance(ALICE_IBAN).compareTo(jdbc.queryForObject(
                "SELECT balance_after FROM ledger_entries WHERE direction = 'DEBIT'", BigDecimal.class)));
        assertEquals(0, balance(BOB_IBAN).compareTo(jdbc.queryForObject(
                "SELECT balance_after FROM ledger_entries WHERE direction = 'CREDIT'", BigDecimal.class)));
    }

    @Test
    void ledgerEntriesAreImmutable() throws Exception {
        flow.pay(login("alice", "Alice123!"), PaymentFlow.aliceToBob("1.00", "immutable"));

        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE ledger_entries SET amount = 99"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM ledger_entries"));
    }

    @Test
    void repeatedFailedLoginsAreRateLimitedWithRetryAfter() throws Exception {
        var body = mapper.writeValueAsBytes(new LoginPayload("brute-force-target", "wrong"));
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_BLOCKED' AND username = 'brute-force-target'", Integer.class));
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND username = 'brute-force-target'", Integer.class));
    }

    @Test
    void auditEntriesCarryTheCorrelationIdOfTheRequest() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Correlation-Id", "audit-corr-1")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(new LoginPayload("alice", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND correlation_id = 'audit-corr-1'", Integer.class));
    }

    @Test
    void preparingPaymentsIsRateLimitedPerUserBecauseItIsAlsoANameCheck() throws Exception {
        String bob = login("bob", "Bob12345!");
        String toAlice = PaymentFlow.body(BOB_IBAN, ALICE_IBAN, "Alice Kask", "1.00", "limit");
        for (int attempt = 0; attempt < 20; attempt++) {
            flow.create(bob, toAlice).andExpect(status().isCreated());
        }

        flow.create(bob, toAlice).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void aPaymentMovesNoMoneyUntilItIsStronglyAuthenticated() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/payments").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(PaymentFlow.aliceToBob("1.00", "bypass")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test
    void reconciliationIsCleanAfterPayments() throws Exception {
        pay(login("alice", "Alice123!"), "10.00");

        var report = reconciliation.reconcile();

        assertEquals(0, report.unbalancedTransactions());
        assertEquals(0, report.balanceMismatches());
        assertEquals(0, report.brokenBalanceChains());
        assertTrue(report.isClean());
    }

    @Test
    void reconciliationDetectsABalanceChangedOutsideTheLedger() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "10.00");
        jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE iban = ?", ALICE_IBAN);

        assertEquals(1, reconciliation.reconcile().balanceMismatches());
    }

    @Test
    void reconciliationDetectsABrokenBalanceChain() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "10.00");
        jdbc.update("UPDATE accounts SET balance = balance + 5 WHERE iban = ?", ALICE_IBAN);
        pay(alice, "10.00");

        var report = reconciliation.reconcile();

        assertEquals(1, report.brokenBalanceChains());
        assertEquals(0, report.balanceMismatches());
    }

    @Test
    void reconciliationDetectsAPostingWithoutItsCounterpart() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "10.00");
        jdbc.execute("ALTER TABLE ledger_entries DISABLE TRIGGER trg_ledger_entries_immutable");
        try {
            jdbc.update("DELETE FROM ledger_entries WHERE direction = 'CREDIT'");
        } finally {
            jdbc.execute("ALTER TABLE ledger_entries ENABLE TRIGGER trg_ledger_entries_immutable");
        }

        assertEquals(1, reconciliation.reconcile().unbalancedTransactions());
    }

    private void pay(String token, String amount) throws Exception {
        flow.pay(token, PaymentFlow.aliceToBob(amount, "ledger-test"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new LoginPayload(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private BigDecimal balance(String iban) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, iban);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record LoginPayload(String username, String password) {}
}