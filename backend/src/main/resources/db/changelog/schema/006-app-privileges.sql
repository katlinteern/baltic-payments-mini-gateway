--liquibase formatted sql

-- The application connects as the unprivileged role payments_app and migrations run as the schema owner. The role owns
-- nothing and has no UPDATE, DELETE or TRUNCATE on the ledger, the transactions and the audit log, so they stay
-- append-only even if the application is compromised or the immutability triggers are dropped. Payments may be
-- updated only in their lifecycle columns. A new table needs its own grant; there are deliberately no default privileges.
-- The grants are idempotent and run on every start, so a role created after the first migration still receives them.
-- Default (prod) context: a missing role halts the migration instead of silently running without least privilege.
-- Contexts local and test: a missing role is tolerated and the application then runs as the schema owner.

--changeset gateway:app-privileges-required context:prod runAlways:true
--preconditions onFail:HALT onFailMessage:Role payments_app must be provisioned before the application starts
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT USAGE ON SCHEMA public TO payments_app;
GRANT SELECT ON users TO payments_app;
GRANT SELECT, UPDATE ON accounts TO payments_app;
GRANT SELECT, INSERT ON transactions, ledger_entries, audit_log, payments TO payments_app;
GRANT UPDATE (status, verification_code, authentication_started_at, expires_at, acknowledged_name_mismatch,
              transaction_id, failure_code, failure_message, updated_at, version) ON payments TO payments_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO payments_app;

--changeset gateway:app-privileges-optional context:local,test runAlways:true
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT USAGE ON SCHEMA public TO payments_app;
GRANT SELECT ON users TO payments_app;
GRANT SELECT, UPDATE ON accounts TO payments_app;
GRANT SELECT, INSERT ON transactions, ledger_entries, audit_log, payments TO payments_app;
GRANT UPDATE (status, verification_code, authentication_started_at, expires_at, acknowledged_name_mismatch,
              transaction_id, failure_code, failure_message, updated_at, version) ON payments TO payments_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO payments_app;
