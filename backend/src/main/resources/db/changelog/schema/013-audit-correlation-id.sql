--liquibase formatted sql

-- Links an audit entry to the request that caused it: the correlation id is also in the logs and in the response.
-- The application role already has INSERT on the whole table, so it needs no new grant.

--changeset gateway:audit-correlation-id
ALTER TABLE audit_log ADD COLUMN correlation_id VARCHAR(64);
CREATE INDEX idx_audit_correlation_id ON audit_log (correlation_id) WHERE correlation_id IS NOT NULL;
