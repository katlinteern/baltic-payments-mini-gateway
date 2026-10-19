--liquibase formatted sql

-- Transaction search is a case-insensitive "contains" match (lower(column) LIKE '%text%'). A btree index cannot serve a
-- leading wildcard, so the earlier (iban, created_at) indexes were never used; trigram indexes on the searched
-- expressions are. pg_trgm is a trusted extension, so the schema owner may create it.

--changeset gateway:transactions-search-trigram-indexes
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP INDEX idx_tx_from_iban;
DROP INDEX idx_tx_to_iban;

CREATE INDEX idx_tx_from_iban_trgm ON transactions USING gin (lower(from_iban) gin_trgm_ops);
CREATE INDEX idx_tx_to_iban_trgm ON transactions USING gin (lower(to_iban) gin_trgm_ops);
CREATE INDEX idx_tx_reference_trgm ON transactions USING gin (lower(reference) gin_trgm_ops);
CREATE INDEX idx_tx_beneficiary_trgm ON transactions USING gin (lower(beneficiary_name) gin_trgm_ops);
CREATE INDEX idx_accounts_holder_trgm ON accounts USING gin (lower(holder_name) gin_trgm_ops);
