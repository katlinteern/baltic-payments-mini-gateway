-- Stands in for the role the platform provisions (db/init/01-app-role.sh in Docker Compose).
CREATE ROLE payments_app LOGIN PASSWORD 'app-test-password';
