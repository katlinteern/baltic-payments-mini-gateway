--liquibase formatted sql

-- Monotonic posting order per account. created_at can tie, so it cannot prove the order of balance_after values.
-- Postings of one account are written under that account's row lock, so identity values increase in booking order.

--changeset gateway:ledger-sequence
ALTER TABLE ledger_entries ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE INDEX idx_ledger_account_seq ON ledger_entries (account_id, seq DESC);
