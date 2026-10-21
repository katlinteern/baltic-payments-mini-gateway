package com.baltic.gateway.accounts.application.ledger;

import java.time.Instant;

public record LedgerReconciliationReport(
        Instant checkedAt, long unbalancedTransactions, long balanceMismatches, long brokenBalanceChains) {
    public long discrepancies() {
        return unbalancedTransactions + balanceMismatches + brokenBalanceChains;
    }

    public boolean isClean() {
        return discrepancies() == 0;
    }
}
