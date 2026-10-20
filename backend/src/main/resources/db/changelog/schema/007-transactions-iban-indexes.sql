--liquibase formatted sql

-- Transaction search filters on either IBAN of a payment; without these indexes it scans the whole table.

--changeset gateway:transactions-iban-indexes
CREATE INDEX idx_tx_from_iban ON transactions (from_iban, created_at DESC);
CREATE INDEX idx_tx_to_iban ON transactions (to_iban, created_at DESC);
