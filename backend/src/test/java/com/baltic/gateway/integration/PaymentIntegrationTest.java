package com.baltic.gateway.integration;

import static com.baltic.gateway.integration.PaymentFlow.ALICE_IBAN;
import static com.baltic.gateway.integration.PaymentFlow.BOB_IBAN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baltic.gateway.identity.application.strongauth.StrongAuthenticator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.ledger.reconciliation.enabled=false", "app.rate-limit.payee-verifications-per-minute=1000"})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class PaymentIntegrationTest {
    static final class ControllableAuthenticator implements StrongAuthenticator {
        volatile Outcome outcome = Outcome.APPROVED;

        @Override public Challenge start(Instant now) { return new Challenge("4821", now); }
        @Override public Outcome outcome(Challenge challenge, Instant now) { return outcome; }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary ControllableAuthenticator controllableAuthenticator() { return new ControllableAuthenticator(); }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ControllableAuthenticator authenticator;
    PaymentFlow flow;
    String alice;

    @BeforeEach
    void reset() throws Exception {
        flow = new PaymentFlow(mvc, mapper);
        authenticator.outcome = StrongAuthenticator.Outcome.APPROVED;
        jdbc.execute("TRUNCATE payments, ledger_entries, transactions");
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("1000.00"), ALICE_IBAN);
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("2500.00"), BOB_IBAN);
        alice = login("alice", "Alice123!");
    }

    @Test
    void preparingAPaymentMovesNoMoneyAndReportsTheNameCheck() throws Exception {
        flow.create(alice, PaymentFlow.aliceToBob("10.00", "prepare"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.nameCheck").value("MATCH"))
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.verificationCode").doesNotExist())
                .andExpect(jsonPath("$.transactionId").doesNotExist());

        assertEquals(0, transactionCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(ALICE_IBAN)));
    }

    @Test
    void theRegisteredNameIsOnlyDisclosedForACloseMatch() throws Exception {
        create("Bob").andExpect(jsonPath("$.nameCheck").value("CLOSE_MATCH"))
                .andExpect(jsonPath("$.registeredName").value("Bob Tamm"));
        create("Bob Tamm").andExpect(jsonPath("$.nameCheck").value("MATCH"))
                .andExpect(jsonPath("$.registeredName").doesNotExist());
        create("Mallory Evil").andExpect(jsonPath("$.nameCheck").value("NO_MATCH"))
                .andExpect(jsonPath("$.registeredName").doesNotExist());
    }

    @Test
    void anApprovedPaymentIsExecutedExactlyOnceWhenItIsRead() throws Exception {
        authenticator.outcome = StrongAuthenticator.Outcome.PENDING;
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("10.00", "approve"));

        flow.confirm(alice, id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_AUTHENTICATION"))
                .andExpect(jsonPath("$.verificationCode").value("4821"));
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("AWAITING_AUTHENTICATION"));
        assertEquals(0, transactionCount());

        authenticator.outcome = StrongAuthenticator.Outcome.APPROVED;
        JsonNode completed = flow.json(flow.poll(alice, id).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.verificationCode").doesNotExist())
                .andExpect(jsonPath("$.balanceAfter").value(990.00)));
        JsonNode again = flow.json(flow.poll(alice, id).andExpect(jsonPath("$.status").value("COMPLETED")));

        assertEquals(completed.get("transactionId"), again.get("transactionId"));
        assertEquals(1, transactionCount());
        assertEquals(0, new BigDecimal("990.00").compareTo(balance(ALICE_IBAN)));
        assertEquals(0, new BigDecimal("2510.00").compareTo(balance(BOB_IBAN)));
    }

    @Test
    void confirmingTwiceKeepsTheSameCode() throws Exception {
        authenticator.outcome = StrongAuthenticator.Outcome.PENDING;
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("1.00", "twice"));

        String first = flow.json(flow.confirm(alice, id, false)).get("verificationCode").asText();
        String second = flow.json(flow.confirm(alice, id, false).andExpect(status().isOk())).get("verificationCode").asText();

        assertEquals(first, second);
    }

    @Test
    void simultaneousReadsOfAnApprovedPaymentPayOnlyOnce() throws Exception {
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("10.00", "race"));
        flow.confirm(alice, id, false).andExpect(status().isOk());

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> flow.poll(alice, id).andReturn().getResponse().getStatus()));
            }
            for (var result : results) {
                int statusCode = result.get();
                assertTrue(statusCode == 200 || statusCode == 409, "unexpected status " + statusCode);
            }
        } finally {
            executor.shutdownNow();
        }
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertEquals(1, transactionCount());
        assertEquals(0, new BigDecimal("990.00").compareTo(balance(ALICE_IBAN)));
    }

    @Test
    void aRejectedAuthenticationEndsThePaymentWithoutMovingMoney() throws Exception {
        authenticator.outcome = StrongAuthenticator.Outcome.REJECTED;
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("10.00", "rejected"));
        flow.confirm(alice, id, false).andExpect(status().isOk());

        flow.poll(alice, id).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AUTHENTICATION_REJECTED"))
                .andExpect(jsonPath("$.verificationCode").doesNotExist());

        assertEquals(0, transactionCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(ALICE_IBAN)));
        assertEquals(1, auditCount("PAYMENT_AUTHENTICATION_REJECTED", "payment=" + id));
    }

    @Test
    void aPaymentThatBecomesUnaffordableBeforeItIsExecutedFailsInsteadOfOverdrawing() throws Exception {
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("900.00", "unaffordable"));
        flow.confirm(alice, id, false).andExpect(status().isOk());
        jdbc.update("UPDATE accounts SET balance = ? WHERE iban = ?", new BigDecimal("5.00"), ALICE_IBAN);

        flow.poll(alice, id).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"));

        assertEquals(0, transactionCount());
        assertEquals(0, new BigDecimal("5.00").compareTo(balance(ALICE_IBAN)));
    }

    @Test
    void aPaymentTheSourceCannotCoverIsRefusedAlreadyWhenItIsPrepared() throws Exception {
        flow.create(alice, PaymentFlow.aliceToBob("5000.00", "too-much"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void aNameMismatchCannotBeConfirmedWithoutAcknowledgingItAndTheAcknowledgementIsRecorded() throws Exception {
        int rejectionsBefore = auditCount("PAYMENT_REJECTED", "%reason=BENEFICIARY_NAME_MISMATCH%");
        String id = flow.createdId(alice, PaymentFlow.body(ALICE_IBAN, BOB_IBAN, "Mallory Evil", "5.00", "mismatch"));

        flow.confirm(alice, id, false).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BENEFICIARY_NAME_MISMATCH"));
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("READY"));
        assertEquals(rejectionsBefore + 1, auditCount("PAYMENT_REJECTED", "%reason=BENEFICIARY_NAME_MISMATCH%"));
        assertEquals(0, transactionCount());

        flow.confirm(alice, id, true).andExpect(status().isOk());
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("COMPLETED"));

        assertEquals("NO_MATCH", jdbc.queryForObject("SELECT name_check FROM transactions", String.class));
        assertEquals(1, auditCount("PAYMENT_EXECUTED", "%mismatchAcknowledgedByPayer=true%"));
        assertEquals(0, new BigDecimal("995.00").compareTo(balance(ALICE_IBAN)));
    }

    @Test
    void aPaymentBelongsToItsPayerAlone() throws Exception {
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("1.00", "private"));
        String bob = login("bob", "Bob12345!");

        flow.poll(bob, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        flow.confirm(bob, id, false).andExpect(status().isNotFound());
        flow.cancel(bob, id).andExpect(status().isNotFound());
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void aCancelledPaymentCannotBeConfirmedAndAnExecutedOneCannotBeCancelled() throws Exception {
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("1.00", "cancel"));
        flow.cancel(alice, id).andExpect(jsonPath("$.status").value("CANCELLED"));
        flow.confirm(alice, id, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_CONFLICT"));

        String paid = flow.pay(alice, PaymentFlow.aliceToBob("1.00", "paid")).get("id").asText();
        flow.cancel(alice, paid).andExpect(status().isConflict());
        flow.poll(alice, paid).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(1, transactionCount());
    }

    @Test
    void aPaymentThatWaitedTooLongExpires() throws Exception {
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("1.00", "late"));
        jdbc.update("UPDATE payments SET expires_at = now() - interval '1 minute'");

        flow.poll(alice, id).andExpect(jsonPath("$.status").value("EXPIRED"));
        flow.confirm(alice, id, false).andExpect(status().isConflict());
        assertEquals(0, transactionCount());
    }

    @Test
    void anAuthenticationThatWasNotApprovedInTimeExpires() throws Exception {
        authenticator.outcome = StrongAuthenticator.Outcome.PENDING;
        String id = flow.createdId(alice, PaymentFlow.aliceToBob("1.00", "slow"));
        flow.confirm(alice, id, false).andExpect(status().isOk());
        jdbc.update("UPDATE payments SET expires_at = now() - interval '1 second'");

        authenticator.outcome = StrongAuthenticator.Outcome.APPROVED;
        flow.poll(alice, id).andExpect(jsonPath("$.status").value("EXPIRED"));
        assertEquals(0, transactionCount());
    }

    @Test
    void preparingIsRefusedForForeignAccountsInvalidInputAndTooLargeOrTooPreciseAmounts() throws Exception {
        flow.create(alice, PaymentFlow.body(BOB_IBAN, ALICE_IBAN, "Alice Kask", "1.00", "foreign"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        flow.create(alice, PaymentFlow.body(ALICE_IBAN, "not-an-iban", "Bob", "1.00", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.toIban").exists());
        flow.create(alice, PaymentFlow.aliceToBob("1.00", "x").replace("EUR", "ZZZ"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.currency").exists());
        flow.create(alice, PaymentFlow.aliceToBob("1000000000000.00", "large"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount can have up to 12 digits and 2 decimals"));
        flow.create(alice, PaymentFlow.aliceToBob("1.005", "precise")).andExpect(status().isBadRequest());
        flow.create(alice, PaymentFlow.body(ALICE_IBAN, ALICE_IBAN, "Alice Kask", "1.00", "self"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TRANSFER"));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    void theAmountKeepsTheScaleOfTheCurrency() throws Exception {
        String created = flow.create(alice, PaymentFlow.aliceToBob("12.30", "scale"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertTrue(created.contains("\"amount\":12.30"), created);
    }

    @Test
    void anAnonymousCallerCannotPrepareAPayment() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(PaymentFlow.aliceToBob("1.00", "anon")))
                .andExpect(status().isUnauthorized());
        assertFalse(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class) > 0);
    }

    private org.springframework.test.web.servlet.ResultActions create(String beneficiaryName) throws Exception {
        return flow.create(alice, PaymentFlow.body(ALICE_IBAN, BOB_IBAN, beneficiaryName, "1.00", "name-check"))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    private int transactionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
    }

    private int auditCount(String action, String detailsLike) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ? AND details LIKE ?",
                Integer.class, action, detailsLike);
    }

    private BigDecimal balance(String iban) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, iban);
    }
}
