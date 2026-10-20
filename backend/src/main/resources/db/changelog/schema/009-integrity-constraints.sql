--liquibase formatted sql

-- Rules the application already enforces, repeated where the data lives so a bug or a manual fix cannot break them.

--changeset gateway:integrity-constraints
ALTER TABLE transactions ADD CONSTRAINT chk_tx_distinct_accounts CHECK (from_account_id <> to_account_id);
ALTER TABLE transactions ADD CONSTRAINT chk_tx_currency CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_currency CHECK (currency ~ '^[A-Z]{3}$');
-- A transfer posts exactly one debit and one credit.
ALTER TABLE ledger_entries ADD CONSTRAINT uq_ledger_transaction_direction UNIQUE (transaction_id, direction);
