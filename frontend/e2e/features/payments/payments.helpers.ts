import { expect, Page } from '@playwright/test';
import { ALICE_IBAN, BOB_IBAN } from '../../common/data';

export interface PaymentForm {
  beneficiaryName: string;
  toIban: string;
  amount: string;
  reference?: string;
}

export const APPROVAL_TIMEOUT = 20_000;

export async function openPaymentForm(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Payments', exact: true }).click();
  await expect(page).toHaveURL(/\/payments$/);
  await page.getByRole('combobox', { name: 'Pay from' }).click();
  await page.getByRole('option', { name: new RegExp(ALICE_IBAN) }).click();
}

export async function fillPayment(page: Page, form: PaymentForm): Promise<void> {
  await page.getByRole('textbox', { name: 'Beneficiary name' }).fill(form.beneficiaryName);
  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).fill(form.toIban);
  await page.getByRole('textbox', { name: 'Amount' }).fill(form.amount);
  await page.getByRole('textbox', { name: 'Payment reference' }).fill(form.reference ?? 'e2e');
}

export async function continueToReview(page: Page): Promise<void> {
  await expect(page.locator('[aria-current="step"]')).toContainText('Details');
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('heading', { name: 'Review payment' })).toBeVisible();
  await expect(page.locator('[aria-current="step"]')).toContainText('Review');
}

export function smartIdDialog(page: Page) {
  return page.getByRole('dialog', { name: 'Confirm with Smart-ID' });
}

export async function confirmAndWaitForReceipt(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  await expect(smartIdDialog(page)).toContainText(/Verification code\s*\d{4}/);
  await expect(page).toHaveURL(/\/payments\/[^/]+\/receipt$/, { timeout: APPROVAL_TIMEOUT });
  await expect(smartIdDialog(page)).toBeHidden();
  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible();
}

export async function payBob(page: Page, amount = '1.00'): Promise<string> {
  const reference = `e2e-${Date.now()}`;
  await openPaymentForm(page);
  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount, reference });
  await continueToReview(page);
  await confirmAndWaitForReceipt(page);
  return reference;
}
