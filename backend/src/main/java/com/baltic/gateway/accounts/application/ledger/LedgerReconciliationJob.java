package com.baltic.gateway.accounts.application.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ledger.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationJob.class);

    private final LedgerReconciliationService service;

    public LedgerReconciliationJob(LedgerReconciliationService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${app.ledger.reconciliation.initial-delay:PT10S}",
            fixedDelayString = "${app.ledger.reconciliation.interval:PT5M}")
    public void run() {
        try {
            service.reconcile();
        } catch (RuntimeException ex) {
            log.error("Ledger reconciliation failed", ex);
        }
    }
}
