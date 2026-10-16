--liquibase formatted sql

-- Demo users and accounts with test-only passwords. Runs only under the Liquibase contexts "local" and "test";
-- the default context is "prod", so none of this exists in a real environment.

--changeset gateway:demo-data context:local,test
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, username, password_hash, role) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'alice',   crypt('Alice123!',   gen_salt('bf', 10)), 'USER'),
    ('00000000-0000-0000-0000-0000000000b1', 'bob',     crypt('Bob12345!',   gen_salt('bf', 10)), 'USER'),
    ('00000000-0000-0000-0000-0000000000c1', 'auditor', crypt('Auditor123!', gen_salt('bf', 10)), 'AUDITOR'),
    ('00000000-0000-0000-0000-0000000000d1', 'admin',   crypt('Admin123!',   gen_salt('bf', 10)), 'ADMIN');

INSERT INTO accounts (id, owner_id, iban, currency, holder_name, balance) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'LT121000011101001000', 'EUR', 'Alice Kask',      1000.00),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a1', 'LV80BANK0000435195001', 'EUR', 'Alice Kask',       500.00),
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000b1', 'EE382200221020145685', 'EUR', 'Bob Tamm',        2500.00),
    ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-0000000000b1', 'DE89370400440532013000', 'EUR', 'Bob Tamm',         750.00),
    ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-0000000000d1', 'GB82WEST12345698765432', 'EUR', 'Baltic Treasury', 10000.00);
