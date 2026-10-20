#!/bin/sh
# Runs once, when the database volume is first created. Creates the unprivileged role the application connects as.
# It owns nothing; changeset app-privileges grants it only the rights it needs (no UPDATE/DELETE/TRUNCATE on the
# ledger or the audit log). Schema migrations keep running as the owner, POSTGRES_USER.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE payments_app LOGIN PASSWORD '${APP_DB_PASSWORD}';
SQL
