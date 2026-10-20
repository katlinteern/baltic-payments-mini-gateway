package com.baltic.gateway.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.ledger.reconciliation.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class TransactionSearchIntegrationTest {
    private static final String ALICE_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String BOB_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final String ALICE_FIRST = "LT121000011101001000";
    private static final String ALICE_SECOND = "LV80BANK0000435195001";
    private static final String BOB_FIRST = "EE382200221020145685";
    private static final String BOB_SECOND = "LT601010012345678901";
    private static final Map<String, String> ACCOUNT_IDS = Map.of(
            ALICE_FIRST, "10000000-0000-0000-0000-000000000001",
            ALICE_SECOND, "10000000-0000-0000-0000-000000000002",
            BOB_FIRST, "10000000-0000-0000-0000-000000000003",
            BOB_SECOND, "10000000-0000-0000-0000-000000000004");

    private static final Instant ALICE_TO_BOB = Instant.parse("2026-03-10T22:30:00Z");
    private static final Instant BOB_TO_BOB = Instant.parse("2026-03-11T10:00:00Z");
    private static final Instant BOB_TO_ALICE = Instant.parse("2026-03-12T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void insertTransactions() {
        jdbc.execute("TRUNCATE payment_intents, ledger_entries, transactions");
        insert(ALICE_ID, ALICE_FIRST, BOB_ID, BOB_FIRST, ALICE_TO_BOB);
        insert(BOB_ID, BOB_FIRST, BOB_ID, BOB_SECOND, BOB_TO_BOB);
        insert(BOB_ID, BOB_FIRST, ALICE_ID, ALICE_SECOND, BOB_TO_ALICE);
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void aUserSeesOnlyPaymentsTheyMadeOrReceivedNewestFirst() throws Exception {
        search("alice", "Alice123!", "")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].createdAt").value(BOB_TO_ALICE.toString()))
                .andExpect(jsonPath("$.content[1].createdAt").value(ALICE_TO_BOB.toString()));
    }

    @Test
    void aUserCannotFindSomeoneElsesPaymentsThroughTheIbanFilter() throws Exception {
        search("alice", "Alice123!", "?q=" + BOB_SECOND).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ibanFilterMatchesEitherSideAndIgnoresSpacesAndCase() throws Exception {
        search("alice", "Alice123!", "?q=" + "lv80 bank 0000 4351 95001")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value(BOB_TO_ALICE.toString()));
    }

    @Test
    void fromIsInclusiveAndToIsExclusive() throws Exception {
        search("bob", "Bob12345!", "?from=" + BOB_TO_BOB + "&to=" + BOB_TO_ALICE)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].createdAt").value(BOB_TO_BOB.toString()));
    }

    @Test
    void theClientDecidesWhereADayStartsSoTallinnMidnightIncludesTheLateUtcPayment() throws Exception {
        search("bob", "Bob12345!", "?from=2026-03-10T22:00:00Z&to=2026-03-11T22:00:00Z")
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void pagingCountsRowsSoATransferBetweenOwnAccountsIsTwo() throws Exception {
        search("bob", "Bob12345!", "")
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(4));
        search("bob", "Bob12345!", "?page=1&size=1")
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].entry").value("DEBIT"));
        search("bob", "Bob12345!", "?page=2&size=1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].entry").value("CREDIT"));
        search("bob", "Bob12345!", "?page=1&size=3")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value(ALICE_TO_BOB.toString()));
    }

    @Test
    void holderNameFindsPaymentsOnEitherSideWithoutBeingAWildcard() throws Exception {
        search("alice", "Alice123!", "?q=tamm").andExpect(jsonPath("$.totalElements").value(2));
        search("alice", "Alice123!", "?q=ta_m").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void historyKeepsTheHolderNamesFromBookingTimeAfterARename() throws Exception {
        String original = jdbc.queryForObject("SELECT holder_name FROM accounts WHERE iban = ?", String.class, BOB_FIRST);
        jdbc.update("UPDATE accounts SET holder_name = 'Robert Renamed' WHERE iban = ?", BOB_FIRST);
        try {
            search("alice", "Alice123!", "?q=renamed").andExpect(jsonPath("$.totalElements").value(0));
            search("alice", "Alice123!", "?q=tamm").andExpect(jsonPath("$.totalElements").value(2));
        } finally {
            jdbc.update("UPDATE accounts SET holder_name = ? WHERE iban = ?", original, BOB_FIRST);
        }
    }

    @Test
    void aPayeeNameHiddenFromThePayerIsNotSearchable() throws Exception {
        jdbc.update("UPDATE transactions SET name_check = 'NO_MATCH' WHERE created_by = ?::uuid", ALICE_ID);
        search("alice", "Alice123!", "?q=tamm").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pagingValuesAreClamped() throws Exception {
        search("alice", "Alice123!", "?page=-3&size=1000")
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    private ResultActions search(String username, String password, String query) throws Exception {
        return mvc.perform(get("/api/transactions" + query).header("Authorization", "Bearer " + login(username, password)))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    private void insert(String creator, String fromIban, String toOwner, String toIban, Instant createdAt) {
        jdbc.update("""
                INSERT INTO transactions (id, from_account_id, to_account_id, from_iban, to_iban, from_name, to_name,
                    beneficiary_name, name_check, created_by, to_owner_id, amount, currency, reference, status,
                    idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, (SELECT holder_name FROM accounts WHERE iban = ?),
                    (SELECT holder_name FROM accounts WHERE iban = ?), 'Test', 'MATCH', ?, ?, ?, 'EUR', 'ref',
                    'COMPLETED', ?, ?)
                """,
                UUID.randomUUID(), UUID.fromString(ACCOUNT_IDS.get(fromIban)), UUID.fromString(ACCOUNT_IDS.get(toIban)),
                fromIban, toIban, fromIban, toIban, UUID.fromString(creator), UUID.fromString(toOwner), new BigDecimal("10.00"),
                UUID.randomUUID().toString(), java.sql.Timestamp.from(createdAt));
    }
}
