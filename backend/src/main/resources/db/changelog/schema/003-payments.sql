--liquibase formatted sql

--changeset gateway:payments-transactions
-- pg_trgm serves the case-insensitive "contains" search: a btree cannot use a leading wildcard.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Holder names are copied at booking time, so the history reads this table only and a rename does not rewrite the past.
CREATE TABLE transactions (
    id               UUID PRIMARY KEY,
    from_account_id  UUID           NOT NULL REFERENCES accounts (id),
    to_account_id    UUID           NOT NULL REFERENCES accounts (id),
    from_iban        VARCHAR(34)    NOT NULL,
    to_iban          VARCHAR(34)    NOT NULL,
    from_name        VARCHAR(120)   NOT NULL,
    to_name          VARCHAR(120)   NOT NULL,
    beneficiary_name VARCHAR(120)   NOT NULL,
    name_check       VARCHAR(16)    NOT NULL CHECK (name_check IN ('MATCH', 'CLOSE_MATCH', 'NO_MATCH', 'NOT_AVAILABLE')),
    created_by       UUID           NOT NULL REFERENCES users (id),
    to_owner_id      UUID           NOT NULL REFERENCES users (id),
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    reference        VARCHAR(140),
    status           VARCHAR(16)    NOT NULL CHECK (status IN ('COMPLETED')),
    idempotency_key  VARCHAR(64)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_tx_idempotency UNIQUE (created_by, idempotency_key),
    CONSTRAINT chk_tx_distinct_accounts CHECK (from_account_id <> to_account_id),
    CONSTRAINT chk_tx_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX idx_tx_created_at ON transactions (created_at DESC);
CREATE INDEX idx_tx_to_owner ON transactions (to_owner_id);
CREATE INDEX idx_tx_from_iban_trgm ON transactions USING gin (lower(from_iban) gin_trgm_ops);
CREATE INDEX idx_tx_to_iban_trgm ON transactions USING gin (lower(to_iban) gin_trgm_ops);
CREATE INDEX idx_tx_reference_trgm ON transactions USING gin (lower(reference) gin_trgm_ops);
CREATE INDEX idx_tx_beneficiary_trgm ON transactions USING gin (lower(beneficiary_name) gin_trgm_ops);
CREATE INDEX idx_tx_from_name_trgm ON transactions USING gin (lower(from_name) gin_trgm_ops);
CREATE INDEX idx_tx_to_name_trgm ON transactions USING gin (lower(to_name) gin_trgm_ops);

--changeset gateway:payments
-- Unlike the ledger a payment is mutable, but the application role may update only the lifecycle columns (see
-- app-privileges), never the details the payer reviewed and authenticated.
CREATE TABLE payments (
    id                         UUID PRIMARY KEY,
    owner_id                   UUID           NOT NULL REFERENCES users (id),
    from_iban                  VARCHAR(34)    NOT NULL,
    to_iban                    VARCHAR(34)    NOT NULL,
    beneficiary_name           VARCHAR(120)   NOT NULL,
    amount                     NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency                   VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    reference                  VARCHAR(140)   NOT NULL,
    name_check                 VARCHAR(16)    NOT NULL CHECK (name_check IN ('MATCH', 'CLOSE_MATCH', 'NO_MATCH', 'NOT_AVAILABLE')),
    registered_name            VARCHAR(120),
    acknowledged_name_mismatch BOOLEAN        NOT NULL DEFAULT FALSE,
    status                     VARCHAR(24)    NOT NULL
        CHECK (status IN ('READY', 'AWAITING_AUTHENTICATION', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    verification_code          VARCHAR(4) CHECK (verification_code ~ '^[0-9]{4}$'),
    authentication_started_at  TIMESTAMPTZ,
    transaction_id             UUID REFERENCES transactions (id),
    failure_code               VARCHAR(48),
    failure_message            VARCHAR(255),
    created_at                 TIMESTAMPTZ    NOT NULL,
    expires_at                 TIMESTAMPTZ    NOT NULL,
    updated_at                 TIMESTAMPTZ    NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_payments_distinct_accounts CHECK (from_iban <> to_iban)
);
CREATE INDEX idx_payments_owner_created ON payments (owner_id, created_at DESC);
