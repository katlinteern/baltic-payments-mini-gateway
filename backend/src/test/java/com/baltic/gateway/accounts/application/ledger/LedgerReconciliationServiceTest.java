package com.baltic.gateway.accounts.application.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baltic.gateway.accounts.infrastructure.ledger.LedgerReconciliationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock LedgerReconciliationRepository repository;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private double finding(String check) {
        return registry.get("ledger.reconciliation.findings").tag("check", check).gauge().value();
    }

    @Test void publishesEachCheckAndTheTotalAsMetrics() {
        when(repository.countUnbalancedTransactions()).thenReturn(1L);
        when(repository.countBalanceMismatches()).thenReturn(2L);
        when(repository.countBrokenBalanceChains()).thenReturn(4L);
        var service = new LedgerReconciliationService(repository, CLOCK, registry);

        var report = service.reconcile();

        assertEquals(7, report.discrepancies());
        assertEquals(7.0, registry.get("ledger.reconciliation.discrepancies").gauge().value());
        assertEquals(1.0, finding("unbalanced_transactions"));
        assertEquals(2.0, finding("balance_mismatches"));
        assertEquals(4.0, finding("broken_balance_chains"));
        assertEquals(CLOCK.instant().getEpochSecond(), registry.get("ledger.reconciliation.last.run.timestamp").gauge().value());
    }

    @Test void aCleanLedgerReportsZeroEverywhere() {
        var service = new LedgerReconciliationService(repository, CLOCK, registry);

        assertEquals(0.0, finding("balance_mismatches"));
        assertTrue(service.reconcile().isClean());
        assertEquals(0.0, registry.get("ledger.reconciliation.discrepancies").gauge().value());
    }
}
