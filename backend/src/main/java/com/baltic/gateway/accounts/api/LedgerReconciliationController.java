package com.baltic.gateway.accounts.api;

import com.baltic.gateway.accounts.application.LedgerReconciliationService;
import com.baltic.gateway.accounts.application.ReconciliationReport;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restricted to the AUDITOR role in the security configuration. */
@RestController
@RequestMapping("/api/ledger/reconciliation")
public class LedgerReconciliationController {
    private final LedgerReconciliationService service;

    public LedgerReconciliationController(LedgerReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Check that ledger postings and account balances agree")
    public ReconciliationReport reconcile() {
        return service.reconcile();
    }
}
