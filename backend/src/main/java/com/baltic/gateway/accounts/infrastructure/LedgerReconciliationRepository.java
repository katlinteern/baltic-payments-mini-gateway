package com.baltic.gateway.accounts.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only integrity queries over the ledger; each returns the number of offending rows. */
@Repository
public class LedgerReconciliationRepository {
    private static final String SIGNED_AMOUNT = "CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END";

    private final JdbcTemplate jdbc;

    public LedgerReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Transactions whose postings do not net to zero, or that mix currencies. */
    public long countUnbalancedTransactions() {
        return count("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id FROM ledger_entries GROUP BY transaction_id
                    HAVING SUM(%s) <> 0 OR COUNT(DISTINCT currency) > 1) unbalanced
                """.formatted(SIGNED_AMOUNT));
    }

    /** Accounts whose stored balance differs from the balance after their latest posting. */
    public long countBalanceMismatches() {
        return count("""
                SELECT COUNT(*) FROM accounts a
                JOIN LATERAL (SELECT balance_after FROM ledger_entries e WHERE e.account_id = a.id
                              ORDER BY e.seq DESC LIMIT 1) latest ON TRUE
                WHERE latest.balance_after <> a.balance
                """);
    }

    /** Postings whose balance_after is not the previous posting's balance_after plus or minus the amount. */
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
