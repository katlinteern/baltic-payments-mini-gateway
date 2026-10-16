--liquibase formatted sql

-- Double-entry postings: every transfer writes one DEBIT and one CREDIT entry. Rows are immutable.
-- seq is the monotonic posting order per account (created_at can tie, so it cannot prove the order of balance_after).
-- Postings of one account are written under that account's row lock, so identity values increase in booking order.

--changeset gateway:ledger-entries
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(8)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL CHECK (balance_after >= 0),
    created_at     TIMESTAMPTZ    NOT NULL,
    seq            BIGINT         GENERATED ALWAYS AS IDENTITY
);
CREATE INDEX idx_ledger_account_seq ON ledger_entries (account_id, seq DESC);
CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);

--changeset gateway:ledger-immutable splitStatements:false
CREATE FUNCTION ledger_entries_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_immutable();
