package com.baltic.gateway.accounts.application.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ledger.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class LedgerReconciliationJob {
    private final LedgerReconciliationService service;

    @Scheduled(
            initialDelayString = "${app.ledger.reconciliation.initial-delay:PT10S}",
            fixedDelayString = "${app.ledger.reconciliation.interval:PT5M}")
    public void run() {
        try {
            service.reconcile();
        } catch (RuntimeException ex) {
            log.error("Ledger reconciliation failed", ex);
        }
    }
}
