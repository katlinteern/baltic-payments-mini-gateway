--liquibase formatted sql

-- The application connects as the unprivileged role payments_app; migrations run as the schema owner.
-- Because the app role owns nothing and has no UPDATE, DELETE or TRUNCATE on the ledger and the audit log, those
-- tables stay append-only even if the application is compromised or the immutability triggers are dropped.
-- The role is provisioned by the platform (db/init in Docker Compose). Where it does not exist the changeset is
-- skipped, and the application then has to run as the schema owner.

--changeset gateway:app-privileges
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM pg_roles WHERE rolname = 'payments_app'
GRANT USAGE ON SCHEMA public TO payments_app;
GRANT SELECT ON users TO payments_app;
GRANT SELECT, UPDATE ON accounts TO payments_app;
GRANT SELECT, INSERT ON transactions, ledger_entries, audit_log TO payments_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO payments_app;
