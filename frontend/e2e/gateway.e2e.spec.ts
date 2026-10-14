import { expect, test } from '@playwright/test';

test('rejects invalid credentials', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill('alice');
  await page.getByRole('textbox', { name: 'Password' }).fill('incorrect-password');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('alert')).toContainText('Login failed');
});

test('alice can log in, pay, and see the completed transfer and updated balance', async ({ page }) => {
  const reference = `e2e-${Date.now()}`;
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill('alice');
  await page.getByRole('textbox', { name: 'Password' }).fill('Alice123!');
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(page.getByRole('row')).toHaveCount(3);

  await page.getByRole('link', { name: 'Payments', exact: true }).click();
  await expect(page).toHaveURL(/\/payments$/);
  await page.getByRole('combobox', { name: 'Pay from' }).click();
  await page.getByRole('option', { name: /LT121000011101001000/ }).click();
  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).fill('EE382200221020145685');
  await page.getByRole('spinbutton', { name: 'Amount' }).fill('1.00');
  await page.getByRole('textbox', { name: 'Payment reference' }).fill(reference);
  await page.getByRole('button', { name: 'Review and send' }).click();
  await expect(page.getByRole('status')).toContainText('Payment completed successfully');

  await page.getByRole('link', { name: 'Activity', exact: true }).click();
  const paymentRow = page.getByRole('row').filter({ hasText: reference });
  await expect(paymentRow).toContainText('1.00 EUR');
  await expect(paymentRow).toContainText('COMPLETED');

  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'LT121000011101001000' })).toContainText('999.00 EUR');
});