--liquibase formatted sql

--changeset gateway:identity-users
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'AUDITOR')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);
