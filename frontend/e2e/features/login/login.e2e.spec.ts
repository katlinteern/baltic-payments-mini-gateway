import { expect, test } from '@playwright/test';
import { signIn } from '../../common/auth';
import { ALICE } from '../../common/data';

test('rejects invalid credentials', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(ALICE.username);
  await page.getByRole('textbox', { name: 'Password' }).fill('incorrect-password');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('alert')).toContainText('Login failed');
});

test('anonymous visitors are redirected to the login page', async ({ page }) => {
  await page.goto('/payments');

  await expect(page).toHaveURL(/\/login$/);
});

test('signing out ends the session and protects the pages again', async ({ page }) => {
  await signIn(page, ALICE);

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/accounts');

  await expect(page).toHaveURL(/\/login$/);
});
