package com.baltic.gateway.accounts.api;

import com.baltic.gateway.accounts.application.LedgerReconciliationService;
import com.baltic.gateway.accounts.application.ReconciliationReport;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * An operations tool, not part of the customer API: it exists only on the management port (8081), which is never
 * published or proxied, so it is reachable from inside the platform only. Customers have no way to read the ledger of
 * others; a bank's finance and operations staff would use an internal tool like this one.
 */
@Component
@Endpoint(id = "reconciliation")
public class LedgerReconciliationEndpoint {
    private final LedgerReconciliationService service;

    public LedgerReconciliationEndpoint(LedgerReconciliationService service) {
        this.service = service;
    }

    @ReadOperation
    public ReconciliationReport reconcile() {
        return service.reconcile();
    }
}
