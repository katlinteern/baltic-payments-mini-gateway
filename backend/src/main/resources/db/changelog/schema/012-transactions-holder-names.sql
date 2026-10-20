--liquibase formatted sql

-- A transaction keeps the holder names of both accounts as they were when it was booked, like a bank statement does.
-- The history and its name search then read only this table; they no longer reach into accounts, and a later change
-- of a holder name does not rewrite past payments.

--changeset gateway:transactions-holder-names
ALTER TABLE transactions ADD COLUMN from_name VARCHAR(120);
ALTER TABLE transactions ADD COLUMN to_name VARCHAR(120);

UPDATE transactions t
SET from_name = (SELECT holder_name FROM accounts WHERE id = t.from_account_id),
    to_name = (SELECT holder_name FROM accounts WHERE id = t.to_account_id);

ALTER TABLE transactions ALTER COLUMN from_name SET NOT NULL;
ALTER TABLE transactions ALTER COLUMN to_name SET NOT NULL;

CREATE INDEX idx_tx_from_name_trgm ON transactions USING gin (lower(from_name) gin_trgm_ops);
CREATE INDEX idx_tx_to_name_trgm ON transactions USING gin (lower(to_name) gin_trgm_ops);

-- Only the holder-name search over accounts used this index.
DROP INDEX idx_accounts_holder_trgm;
