package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class GatewayApiIntegrationTest {
    private static final String ALICE_IBAN = "LT121000011101001000";
    private static final String ALICE_SECOND_IBAN = "LV80BANK0000435195001";
    private static final String BOB_IBAN = "EE382200221020145685";
    private static final String ALICE_ACCOUNT_ID = "10000000-0000-0000-0000-000000000001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetPaymentFixtures() {
        jdbc.execute("TRUNCATE ledger_entries, transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("500.00"), ALICE_SECOND_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
    }

    @Test
    void authenticatedAccountsAndRoleProtectedAuditEndpointsWork() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        String aliceToken = login("alice", "Alice123!");
        mvc.perform(get("/api/accounts").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/audit").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());

        String auditorToken = login("auditor", "Auditor123!");
        mvc.perform(get("/api/audit").header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void paymentIsPersistedOnceAndSameKeyWithChangedReferenceIsRejected() throws Exception {
        String token = login("alice", "Alice123!");
        String key = "integration-pay-key-001";
        String request = paymentBody("10.00", "integration-reference");

        MvcResult first = mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.00))
                .andReturn();

        JsonNode firstBody = mapper.readTree(first.getResponse().getContentAsString());
        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(firstBody.get("id").asText()));

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("10.00", "different-reference")))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, new BigDecimal("990.00").compareTo(balance(ALICE_IBAN)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test
    void paymentCannotDebitAnAccountOwnedByAnotherUser() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "foreign-account-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromIban":"EE382200221020145685","toIban":"LV80BANK0000435195001","beneficiaryName":"Alice","amount":1.00,"currency":"EUR","reference":"forbidden"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertEquals(0, new BigDecimal("2500.00").compareTo(balance(BOB_IBAN)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test
    void invalidIbanReturnsProblemDetailsWithoutCreatingPayment() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "invalid-iban-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromIban":"LT121000011101001000","toIban":"not-an-iban","beneficiaryName":"Bob","amount":1.00,"currency":"EUR","reference":"invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.toIban").exists());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test
    void paymentBooksBalancedLedgerEntriesThatMatchAccountBalances() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "ledger-key-0000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("25.50", "ledger")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameCheck").value("MATCH"));

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
        String token = login("alice", "Alice123!");
        mvc.perform(post("/api/payments")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "immutable-key-00001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentBody("1.00", "immutable"))).andExpect(status().isCreated());

        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE ledger_entries SET amount = 99"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM ledger_entries"));
    }

    @Test
    void wrongBeneficiaryNameIsRejectedAndAuditedWithoutMovingMoney() throws Exception {
        String token = login("alice", "Alice123!");
        int rejectionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_REJECTED' AND details = 'Beneficiary name mismatch'", Integer.class);

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "wrong-name-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("1.00", "x").replace("Bob Tamm", "Mallory Evil")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Beneficiary name mismatch"));

        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(ALICE_IBAN)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
        assertEquals(rejectionsBefore + 1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_REJECTED' AND details = 'Beneficiary name mismatch'", Integer.class));
    }

    @Test
    void unknownCurrencyIsAValidationError() throws Exception {
        String token = login("alice", "Alice123!");

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "bad-currency-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("1.00", "x").replace("EUR", "ZZZ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
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
    }

    @Test
    void payeeVerificationReportsTheOutcomeAndOnlyDisclosesTheRegisteredNameForACloseMatch() throws Exception {
        String token = login("alice", "Alice123!");

        verifyPayee(token, BOB_IBAN, "Bob Tamm").andExpect(jsonPath("$.result").value("MATCH"))
                .andExpect(jsonPath("$.registeredName").doesNotExist());
        verifyPayee(token, BOB_IBAN, "Bob").andExpect(jsonPath("$.result").value("CLOSE_MATCH"))
                .andExpect(jsonPath("$.registeredName").value("Bob Tamm"));
        verifyPayee(token, BOB_IBAN, "Mallory Evil").andExpect(jsonPath("$.result").value("NO_MATCH"))
                .andExpect(jsonPath("$.registeredName").doesNotExist());
        verifyPayee(token, "DE75512108001245126199", "Anyone").andExpect(jsonPath("$.result").value("NOT_AVAILABLE"));
        mvc.perform(post("/api/payments/verify-payee").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toIban\":\"" + BOB_IBAN + "\",\"beneficiaryName\":\"Bob\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payeeVerificationIsRateLimitedPerUser() throws Exception {
        String token = login("bob", "Bob12345!");
        for (int attempt = 0; attempt < 20; attempt++) {
            verifyPayee(token, BOB_IBAN, "Guess " + attempt).andExpect(status().isOk());
        }

        mvc.perform(post("/api/payments/verify-payee")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toIban\":\"" + BOB_IBAN + "\",\"beneficiaryName\":\"One more\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void payerCanSendDespiteANameMismatchOnlyByAcknowledgingItAndTheMismatchIsRecorded() throws Exception {
        String token = login("alice", "Alice123!");
        String mismatching = paymentBody("5.00", "acknowledged").replace("Bob Tamm", "Mallory Evil");

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "mismatch-ack-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatching))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "mismatch-ack-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatching.replace("\"reference\"", "\"proceedDespiteNameMismatch\":true,\"reference\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameCheck").value("NO_MATCH"));

        assertEquals(0, new BigDecimal("995.00").compareTo(balance(ALICE_IBAN)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_EXECUTED' AND details LIKE '%mismatchAcknowledgedByPayer=true%'",
                Integer.class));
    }

    @Test
    void statementListsPostingsNewestFirstAndOnlyForTheAccountOwner() throws Exception {
        String alice = login("alice", "Alice123!");
        String bob = login("bob", "Bob12345!");
        pay(alice, "statement-key-0001", "10.00");
        pay(alice, "statement-key-0002", "5.00");

        mvc.perform(get("/api/accounts/{id}/ledger", ALICE_ACCOUNT_ID).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].amount").value(5.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(985.00))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(990.00));
        mvc.perform(get("/api/accounts/{id}/ledger", ALICE_ACCOUNT_ID).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reconciliationIsCleanAfterPaymentsAndOnlyForAuditors() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "reconcile-key-0001", "10.00");

        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(login("auditor", "Auditor123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unbalancedTransactions").value(0))
                .andExpect(jsonPath("$.balanceMismatches").value(0))
                .andExpect(jsonPath("$.brokenBalanceChains").value(0));
    }

    @Test
    void reconciliationDetectsABalanceChangedOutsideTheLedger() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "reconcile-key-0002", "10.00");
        jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE iban = ?", ALICE_IBAN);

        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(login("auditor", "Auditor123!"))))
                .andExpect(jsonPath("$.balanceMismatches").value(1));
    }

    @Test
    void reconciliationDetectsABrokenBalanceChain() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "reconcile-key-0003", "10.00");
        jdbc.update("UPDATE accounts SET balance = balance + 5 WHERE iban = ?", ALICE_IBAN);
        pay(alice, "reconcile-key-0004", "10.00");

        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(login("auditor", "Auditor123!"))))
                .andExpect(jsonPath("$.brokenBalanceChains").value(1))
                .andExpect(jsonPath("$.balanceMismatches").value(0));
    }

    @Test
    void reconciliationDetectsAPostingWithoutItsCounterpart() throws Exception {
        String alice = login("alice", "Alice123!");
        pay(alice, "reconcile-key-0005", "10.00");
        jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, account_id, direction, amount, currency, balance_after, created_at)
                SELECT gen_random_uuid(), transaction_id, account_id, 'CREDIT', 1.00, currency, balance_after + 1, created_at
                FROM ledger_entries WHERE direction = 'CREDIT' LIMIT 1
                """);

        mvc.perform(get("/api/ledger/reconciliation").header("Authorization", bearer(login("auditor", "Auditor123!"))))
                .andExpect(jsonPath("$.unbalancedTransactions").value(1));
    }

    private void pay(String token, String key, String amount) throws Exception {
        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(amount, "ledger-test")))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions verifyPayee(String token, String iban, String name) throws Exception {
        return mvc.perform(post("/api/payments/verify-payee")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toIban\":\"%s\",\"beneficiaryName\":\"%s\"}".formatted(iban, name)));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new LoginPayload(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String paymentBody(String amount, String reference) {
        return """
                {"fromIban":"LT121000011101001000","toIban":"EE382200221020145685","beneficiaryName":"Bob Tamm","amount":%s,"currency":"EUR","reference":"%s"}
                """.formatted(amount, reference);
    }

    private BigDecimal balance(String iban) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, iban);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record LoginPayload(String username, String password) {}
}