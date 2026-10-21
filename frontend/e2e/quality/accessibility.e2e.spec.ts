import { expect, test } from '@playwright/test';
import { signIn } from '../common/auth';
import { ALICE, ALICE_IBAN, BOB_IBAN } from '../common/data';
import {
  APPROVAL_TIMEOUT,
  continueToReview,
  fillPayment,
  openPaymentForm,
  smartIdDialog,
} from '../features/payments/payments.helpers';
import { expectNoAccessibilityViolations } from './accessibility.helpers';

test('every page and every step of a payment pass automated WCAG 2.1 AA checks', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await signIn(page, ALICE);
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await page.getByRole('link', { name: 'Transactions', exact: true }).click();
  await expect(page).toHaveURL(/\/transactions$/);
  await expectNoAccessibilityViolations(page);

  await openPaymentForm(page);
  await expectNoAccessibilityViolations(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);
  await expectNoAccessibilityViolations(page);

  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  await expect(smartIdDialog(page)).toContainText(/Verification code\s*\d{4}/);
  await expectNoAccessibilityViolations(page);

  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible({ timeout: APPROVAL_TIMEOUT });
  await expectNoAccessibilityViolations(page);
});
