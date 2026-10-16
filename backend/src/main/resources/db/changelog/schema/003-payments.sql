--liquibase formatted sql

--changeset gateway:payments-transactions
CREATE TABLE transactions (
    id               UUID PRIMARY KEY,
    from_account_id  UUID           NOT NULL REFERENCES accounts (id),
    to_account_id    UUID           NOT NULL REFERENCES accounts (id),
    from_iban        VARCHAR(34)    NOT NULL,
    to_iban          VARCHAR(34)    NOT NULL,
    beneficiary_name VARCHAR(120)   NOT NULL,
    -- Result of comparing the payer-typed beneficiary name with the destination account holder.
    name_check       VARCHAR(16)    NOT NULL CHECK (name_check IN ('MATCH', 'CLOSE_MATCH', 'NO_MATCH', 'NOT_AVAILABLE')),
    created_by       UUID           NOT NULL REFERENCES users (id),
    to_owner_id      UUID           NOT NULL REFERENCES users (id),
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    reference        VARCHAR(140),
    status           VARCHAR(16)    NOT NULL CHECK (status IN ('COMPLETED')),
    idempotency_key  VARCHAR(64)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_tx_idempotency UNIQUE (created_by, idempotency_key)
);
CREATE INDEX idx_tx_created_at ON transactions (created_at DESC);
CREATE INDEX idx_tx_to_owner ON transactions (to_owner_id);
