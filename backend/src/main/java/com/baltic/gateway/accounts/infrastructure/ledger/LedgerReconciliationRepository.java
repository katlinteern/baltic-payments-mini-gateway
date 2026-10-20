package com.baltic.gateway.accounts.infrastructure.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerReconciliationRepository {
    private static final String SIGNED_AMOUNT = "CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END";

    private final JdbcTemplate jdbc;

    public LedgerReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countUnbalancedTransactions() {
        return count("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id FROM ledger_entries GROUP BY transaction_id
                    HAVING SUM(%s) <> 0 OR COUNT(DISTINCT currency) > 1) unbalanced
                """.formatted(SIGNED_AMOUNT));
    }

    public long countBalanceMismatches() {
        return count("""
                SELECT COUNT(*) FROM accounts a
                JOIN LATERAL (SELECT balance_after FROM ledger_entries e WHERE e.account_id = a.id
                              ORDER BY e.seq DESC LIMIT 1) latest ON TRUE
                WHERE latest.balance_after <> a.balance
                """);
    }

    public long countBrokenBalanceChains() {
        return count("""
                SELECT COUNT(*) FROM (
                    SELECT balance_after, %s AS signed,
                           LAG(balance_after) OVER (PARTITION BY account_id ORDER BY seq) AS previous
                    FROM ledger_entries) chained
                WHERE previous IS NOT NULL AND balance_after <> previous + signed
                """.formatted(SIGNED_AMOUNT));
    }

    private long count(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }
}
