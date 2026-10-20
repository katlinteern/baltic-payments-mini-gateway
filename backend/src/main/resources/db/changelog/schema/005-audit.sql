--liquibase formatted sql

--changeset gateway:audit-log
CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    action         VARCHAR(48) NOT NULL,
    user_id        UUID,
    username       VARCHAR(64),
    ip_address     VARCHAR(64),
    details        TEXT,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_correlation_id ON audit_log (correlation_id) WHERE correlation_id IS NOT NULL;

--changeset gateway:audit-immutable splitStatements:false
CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
