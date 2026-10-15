package lt.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetPaymentFixtures() {
        jdbc.update("DELETE FROM transactions");
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
                {"fromIban":"LT121000011101001000","toIban":"EE382200221020145685","beneficiaryName":"Bob","amount":%s,"currency":"EUR","reference":"%s"}
                """.formatted(amount, reference);
    }

    private BigDecimal balance(String iban) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE iban = ?", BigDecimal.class, iban);
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private record LoginPayload(String username, String password) {}
}