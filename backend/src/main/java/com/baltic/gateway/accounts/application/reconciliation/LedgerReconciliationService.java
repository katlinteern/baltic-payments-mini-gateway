package com.baltic.gateway.accounts.application.reconciliation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import com.baltic.gateway.accounts.infrastructure.ledger.LedgerReconciliationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that the ledger and the account balances agree: every transaction nets to zero, every posting continues
 * the balance chain of its account, and the latest posting equals the stored balance. The result is published as
 * metrics so an alert fires on any discrepancy. Balances loaded outside the ledger (the demo seed) are covered from
 * the first posting onward.
 */
@Service
public class LedgerReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationService.class);

    private final LedgerReconciliationRepository repository;
    private final Clock clock;
    private final AtomicLong discrepancies = new AtomicLong();
    private final AtomicLong unbalancedTransactions = new AtomicLong();
    private final AtomicLong balanceMismatches = new AtomicLong();
    private final AtomicLong brokenBalanceChains = new AtomicLong();
    private final AtomicLong lastRunEpochSeconds = new AtomicLong();

    public LedgerReconciliationService(LedgerReconciliationRepository repository, Clock clock, MeterRegistry registry) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("ledger.reconciliation.discrepancies", discrepancies, AtomicLong::get)
                .description("Ledger postings and account balances that disagree after the last reconciliation")
                .register(registry);
        findings(registry, "unbalanced_transactions", unbalancedTransactions);
        findings(registry, "balance_mismatches", balanceMismatches);
        findings(registry, "broken_balance_chains", brokenBalanceChains);
        Gauge.builder("ledger.reconciliation.last.run.timestamp", lastRunEpochSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .description("When the ledger was last reconciled")
                .register(registry);
    }

    private static void findings(MeterRegistry registry, String check, AtomicLong value) {
        Gauge.builder("ledger.reconciliation.findings", value, AtomicLong::get)
                .tag("check", check)
                .description("Offending rows found by one ledger check in the last reconciliation")
                .register(registry);
    }

    /** One consistent snapshot: the three checks must not see payments that commit between them. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport reconcile() {
        var report = new ReconciliationReport(clock.instant(), repository.countUnbalancedTransactions(),
                repository.countBalanceMismatches(), repository.countBrokenBalanceChains());
        discrepancies.set(report.discrepancies());
        unbalancedTransactions.set(report.unbalancedTransactions());
        balanceMismatches.set(report.balanceMismatches());
        brokenBalanceChains.set(report.brokenBalanceChains());
        lastRunEpochSeconds.set(report.checkedAt().getEpochSecond());
        if (!report.isClean()) {
            log.error("Ledger reconciliation found discrepancies: unbalancedTransactions={} balanceMismatches={} brokenBalanceChains={}",
                    report.unbalancedTransactions(), report.balanceMismatches(), report.brokenBalanceChains());
        }
        return report;
    }
}
