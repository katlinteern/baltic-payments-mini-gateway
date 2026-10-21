import { expect, test } from '@playwright/test';
import { signIn } from '../../common/auth';
import { ALICE, ALICE_IBAN, BOB, BOB_IBAN } from '../../common/data';
import { payBob } from '../payments/payments.helpers';
import { formatEur, readBalance } from './accounts.helpers';

test('alice sees her own accounts with their holder name', async ({ page }) => {
  await signIn(page, ALICE);

  await expect(page.getByRole('row')).toHaveCount(3);
  await expect(page.getByText('Alice Kask').first()).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toBeVisible();
});

test('a user does not see the accounts of other users', async ({ page }) => {
  await signIn(page, BOB);

  await expect(page.getByRole('row').filter({ hasText: BOB_IBAN })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toHaveCount(0);
});

test('the account balance is reduced after a payment', async ({ page }) => {
  await signIn(page, ALICE);
  const before = await readBalance(page, ALICE_IBAN);

  await payBob(page);
  await page.getByRole('link', { name: 'Accounts', exact: true }).first().click();

  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toContainText(formatEur(before - 1));
});
