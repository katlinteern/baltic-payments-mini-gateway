import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const projectName = 'baltic-payments-e2e';
const repositoryRoot = resolve(process.cwd(), '..');

const environment = {
  ...process.env,
  BACKEND_PORT: '18080',
  FRONTEND_PORT: '14200',
  DB_PASSWORD: 'isolated-e2e-database',
  APP_DB_PASSWORD: 'isolated-e2e-app-database',
  JWT_SECRET: 'isolated-e2e-jwt-secret-with-more-than-32-characters',
  SPRING_PROFILES_ACTIVE: 'local',
};

export function runE2eCompose(args: string[]): void {
  execFileSync('docker', ['compose', '--project-name', projectName, ...args], {
    cwd: repositoryRoot,
    env: environment,
    stdio: 'inherit',
  });
}

export async function waitForE2eStack(): Promise<void> {
  const deadline = Date.now() + 120_000;
  let lastError: unknown;

  while (Date.now() < deadline) {
    try {
      const [frontend, api] = await Promise.all([
        fetch('http://127.0.0.1:14200'),
        fetch('http://127.0.0.1:14200/api/accounts'),
      ]);
      // An unauthenticated 401 proves the request travelled through nginx to a running backend.
      if (frontend.ok && api.status === 401) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 1000));
  }

  throw new Error('E2E Compose stack did not become ready within 120 seconds.', { cause: lastError });
}
