import { expect, test } from '@playwright/test';
import { signIn } from '../common/auth';
import { ALICE } from '../common/data';

test('the app runs without Content Security Policy violations', async ({ page }) => {
  const violations: string[] = [];
  page.on('console', (message) => {
    if (message.text().includes('Content Security Policy')) violations.push(message.text());
  });

  const response = await page.goto('/login');
  expect(response?.headers()['content-security-policy']).toContain("default-src 'self'");
  await signIn(page, ALICE);
  await page.getByRole('link', { name: 'Transactions', exact: true }).click();
  await expect(page).toHaveURL(/\/transactions$/);

  expect(violations).toEqual([]);
});
