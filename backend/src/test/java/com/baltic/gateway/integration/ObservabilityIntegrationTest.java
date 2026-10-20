package com.baltic.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Scrapes the real Prometheus endpoint. The Prometheus registry silently drops meters whose tag keys disagree, so
 * asserting on the exposition text (not on a SimpleMeterRegistry) is what proves the metrics are actually exported.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"management.server.port=0", "app.ledger.reconciliation.enabled=false", "app.strong-auth.mock.delay=PT0S"})
@AutoConfigureObservability
@Testcontainers
@ActiveProfiles("local")
class ObservabilityIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;
    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate rest;

    @Test void prometheusEndpointExposesBusinessAndHttpMetricsOnTheManagementPortOnly() {
        String base = "http://localhost:" + port;
        var failedLogin = rest.postForEntity(base + "/api/auth/login",
                Map.of("username", "alice", "password", "wrong-password"), String.class);
        assertEquals(401, failedLogin.getStatusCode().value());

        String token = (String) rest.postForObject(base + "/api/auth/login",
                Map.of("username", "alice", "password", "Alice123!"), Map.class).get("token");
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var prepared = rest.postForEntity(base + "/api/payments/intents", new HttpEntity<>("""
                {"fromIban":"LT121000011101001000","toIban":"EE382200221020145685","beneficiaryName":"Mallory Evil",
                 "amount":1.00,"currency":"EUR","reference":"observability"}""", headers), Map.class);
        assertEquals(201, prepared.getStatusCode().value());
        var rejected = rest.postForEntity(base + "/api/payments/intents/" + prepared.getBody().get("id") + "/confirmation",
                new HttpEntity<>("{\"acknowledgeNameMismatch\":false}", headers), String.class);
        assertEquals(422, rejected.getStatusCode().value());

        var scrape = rest.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class);

        assertEquals(200, scrape.getStatusCode().value());
        String body = scrape.getBody();
        assertTrue(hasSeries(body, "auth_logins_total", "outcome=\"failed\"", "application=\"baltic-payments-gateway\""), body);
        assertTrue(hasSeries(body, "auth_logins_total", "outcome=\"success\""), body);
        assertTrue(hasSeries(body, "payments_total", "outcome=\"rejected\"", "reason=\"beneficiary_name_mismatch\"", "currency=\"EUR\""), body);
        assertTrue(body.contains("payments_processing_seconds_bucket"));
        assertTrue(body.contains("http_server_requests_seconds_bucket"));

        var publicPort = rest.getForEntity(base + "/actuator/prometheus", String.class);
        assertNotEquals(200, publicPort.getStatusCode().value());
    }

    @Test void ledgerReconciliationIsAnOperationsEndpointOnTheManagementPortOnly() {
        var report = rest.getForEntity("http://localhost:" + managementPort + "/actuator/reconciliation", Map.class);

        assertEquals(200, report.getStatusCode().value());
        assertEquals(0, report.getBody().get("unbalancedTransactions"));
        assertEquals(0, report.getBody().get("balanceMismatches"));
        assertEquals(0, report.getBody().get("brokenBalanceChains"));

        var publicPort = rest.getForEntity("http://localhost:" + port + "/actuator/reconciliation", String.class);
        assertNotEquals(200, publicPort.getStatusCode().value());
    }

    private static boolean hasSeries(String exposition, String metric, String... labels) {
        return Arrays.stream(exposition.split("\n"))
                .filter(line -> line.startsWith(metric + "{"))
                .anyMatch(line -> Arrays.stream(labels).allMatch(line::contains));
    }
}
