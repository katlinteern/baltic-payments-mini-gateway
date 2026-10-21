import { expect, test } from '@playwright/test';
import { signIn } from '../../common/auth';
import { ALICE, BOB_IBAN } from '../../common/data';
import { payBob } from '../payments/payments.helpers';

test('a completed payment is listed with its details', async ({ page }) => {
  await signIn(page, ALICE);
  const reference = await payBob(page);

  await page.getByRole('link', { name: 'Transactions', exact: true }).first().click();
  const paymentRow = page.getByRole('row').filter({ hasText: reference });
  await expect(paymentRow).toContainText('−1.00 EUR');
  await expect(paymentRow).toContainText('Bob Tamm');
  await paymentRow.click();

  const details = page.getByRole('dialog');
  await expect(details).toContainText('Alice Kask');
  await expect(details).toContainText('Bob Tamm');
  await expect(details).toContainText(BOB_IBAN);
  await details.getByRole('button', { name: 'Close' }).click();
  await expect(details).toBeHidden();
});

test('the transactions can be filtered by date', async ({ page }) => {
  await signIn(page, ALICE);
  const reference = await payBob(page);
  await page.getByRole('link', { name: 'Transactions', exact: true }).first().click();
  const paymentRow = page.getByRole('row').filter({ hasText: reference });

  const today = new Date().toLocaleDateString('en-US');
  await page.getByLabel('From date').fill(today);
  await page.getByLabel('To date').fill(today);
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(paymentRow).toBeVisible();

  await page.getByLabel('To date').fill('1/1/2000');
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(page.getByText('No payments match these filters.')).toBeVisible();
});
