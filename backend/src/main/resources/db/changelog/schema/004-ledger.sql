--liquibase formatted sql

-- Double-entry postings: a transfer writes one DEBIT and one CREDIT. seq orders the postings of one account; they are
-- written under that account's row lock, so identity values increase in booking order (created_at can tie).

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
    seq            BIGINT         GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT chk_ledger_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_ledger_transaction_direction UNIQUE (transaction_id, direction)
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
