package com.baltic.gateway.accounts.api.reconciliation;

import com.baltic.gateway.accounts.application.reconciliation.LedgerReconciliationService;
import com.baltic.gateway.accounts.application.reconciliation.ReconciliationReport;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * An operations tool, not part of the customer API: it exists only on the management port (8081), which is never
 * published or proxied.
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
