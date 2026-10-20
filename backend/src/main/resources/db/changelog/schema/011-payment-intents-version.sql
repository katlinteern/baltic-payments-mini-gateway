--liquibase formatted sql

-- Optimistic version of a payment intent: a write based on a stale copy fails instead of undoing a newer state.

--changeset gateway:payment-intents-version
ALTER TABLE payment_intents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

--changeset gateway:payment-intents-version-privileges-required context:prod runAlways:true
--preconditions onFail:HALT onFailMessage:Role payments_app must be provisioned before the application starts
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT UPDATE (version) ON payment_intents TO payments_app;

--changeset gateway:payment-intents-version-privileges-optional context:local,test runAlways:true
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT UPDATE (version) ON payment_intents TO payments_app;
