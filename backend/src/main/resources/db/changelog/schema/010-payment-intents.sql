--liquibase formatted sql

-- A payment is first prepared as an intent (reviewed by the payer, name-checked by the server), then authenticated, and
-- only then executed. The intent is mutable state, so unlike the ledger it may be updated, but the application role
-- may change only the lifecycle columns, never the payment details the payer reviewed and authenticated.

--changeset gateway:payment-intents
CREATE TABLE payment_intents (
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
    CONSTRAINT chk_intent_distinct_accounts CHECK (from_iban <> to_iban)
);
CREATE INDEX idx_intents_owner_created ON payment_intents (owner_id, created_at DESC);

--changeset gateway:payment-intents-privileges-required context:prod runAlways:true
--preconditions onFail:HALT onFailMessage:Role payments_app must be provisioned before the application starts
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT SELECT, INSERT ON payment_intents TO payments_app;
GRANT UPDATE (status, verification_code, authentication_started_at, expires_at, acknowledged_name_mismatch,
              transaction_id, failure_code, failure_message, updated_at) ON payment_intents TO payments_app;

--changeset gateway:payment-intents-privileges-optional context:local,test runAlways:true
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT SELECT, INSERT ON payment_intents TO payments_app;
GRANT UPDATE (status, verification_code, authentication_started_at, expires_at, acknowledged_name_mismatch,
              transaction_id, failure_code, failure_message, updated_at) ON payment_intents TO payments_app;
