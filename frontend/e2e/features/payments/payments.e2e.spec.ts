import { expect, test } from '@playwright/test';
import { signIn } from '../../common/auth';
import { ALICE, ALICE_IBAN, BOB_IBAN } from '../../common/data';
import { formatEur, readBalance } from '../accounts/accounts.helpers';
import {
  confirmAndWaitForReceipt,
  continueToReview,
  fillPayment,
  openPaymentForm,
  smartIdDialog,
} from './payments.helpers';

test('alice can pay and sees the receipt with the updated balance', async ({ page }) => {
  const reference = `e2e-${Date.now()}`;
  await signIn(page, ALICE);
  const before = await readBalance(page, ALICE_IBAN);

  await openPaymentForm(page);
  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00', reference });
  await continueToReview(page);
  await expect(page.getByText('The name matches the account holder.')).toBeVisible();
  await expect(page.getByText('0.00 EUR')).toBeVisible();
  await confirmAndWaitForReceipt(page);

  const receipt = page.getByRole('main');
  await expect(receipt).toContainText('1.00 EUR');
  await expect(receipt).toContainText('Bob Tamm');
  await expect(receipt).toContainText(reference);
  await expect(receipt).toContainText(formatEur(before - 1));

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible();
});

test('the payment form blocks submission until required fields are valid', async ({ page }) => {
  await signIn(page, ALICE);
  await openPaymentForm(page);

  const submit = page.getByRole('button', { name: 'Continue' });
  await expect(submit).toBeDisabled();
  for (const label of ['Beneficiary name', 'Beneficiary IBAN']) {
    await page.getByRole('textbox', { name: label }).focus();
    await page.getByRole('textbox', { name: label }).blur();
  }
  await page.getByRole('textbox', { name: 'Amount' }).focus();
  await page.getByRole('textbox', { name: 'Amount' }).blur();
  await expect(page.getByText('Enter the beneficiary name')).toBeVisible();
  await expect(page.getByText('Enter the beneficiary IBAN')).toBeVisible();
  await expect(page.getByText('Enter an amount')).toBeVisible();

  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).fill('LT131000011101001000');
  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).blur();
  await expect(page.getByText('Enter a valid IBAN')).toBeVisible();
  await expect(submit).toBeDisabled();
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('a beneficiary name that does not match the account holder must be acknowledged before the payer can confirm', async ({
  page,
}) => {
  await signIn(page, ALICE);
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Mallory Evil', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);

  await expect(page.getByRole('alert')).toContainText('does not match the holder of this account');
  const confirm = page.getByRole('button', { name: 'Confirm with Smart-ID' });
  await expect(confirm).toBeDisabled();
  await page.getByRole('checkbox', { name: /send the payment anyway/ }).check();
  await expect(confirm).toBeEnabled();
  await confirmAndWaitForReceipt(page);
});

test('the payer sees the registered name of a close match and can go back and edit', async ({ page }) => {
  await signIn(page, ALICE);
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);
  await expect(page.getByText('registered as Bob Tamm')).toBeVisible();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByRole('heading', { name: 'New payment' })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Beneficiary name' })).toHaveValue('Bob');
  await expect(page).toHaveURL(/\/payments$/);
});

test('the payer can cancel while waiting for the Smart-ID approval, and nothing is paid', async ({ page }) => {
  await signIn(page, ALICE);
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);
  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  const dialog = smartIdDialog(page);
  await expect(dialog).toContainText(/Verification code\s*\d{4}/);
  await expect(dialog.getByText(/Time left \d:\d{2}/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Review payment' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel' }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole('heading', { name: 'New payment' })).toBeVisible();
  await expect(page).toHaveURL(/\/payments$/);
});

test('a payment larger than the balance is refused already when it is prepared', async ({ page }) => {
  await signIn(page, ALICE);
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '999999.00' });
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('alert')).toContainText('Balance is too low');
  await expect(page.getByRole('heading', { name: 'New payment' })).toBeVisible();
});
