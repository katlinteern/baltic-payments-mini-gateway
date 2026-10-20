--liquibase formatted sql

--changeset gateway:accounts-accounts
CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    owner_id    UUID           NOT NULL REFERENCES users (id),
    iban        VARCHAR(34)    NOT NULL UNIQUE,
    currency    VARCHAR(3)     NOT NULL,
    holder_name VARCHAR(120)   NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL CHECK (balance >= 0),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX idx_accounts_owner ON accounts (owner_id);
