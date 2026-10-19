import { expect, Page, test } from '@playwright/test';

const ALICE_IBAN = 'LT121000011101001000';
const BOB_IBAN = 'EE382200221020145685';

async function signIn(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/accounts$/);
}

interface PaymentForm {
  beneficiaryName: string;
  toIban: string;
  amount: string;
  reference?: string;
}

async function openPaymentForm(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Payments', exact: true }).click();
  await expect(page).toHaveURL(/\/payments$/);
  await page.getByRole('combobox', { name: 'Pay from' }).click();
  await page.getByRole('option', { name: new RegExp(ALICE_IBAN) }).click();
}

async function fillPayment(page: Page, form: PaymentForm): Promise<void> {
  await page.getByRole('textbox', { name: 'Beneficiary name' }).fill(form.beneficiaryName);
  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).fill(form.toIban);
  await page.getByRole('spinbutton', { name: 'Amount' }).fill(form.amount);
  await page.getByRole('textbox', { name: 'Payment reference' }).fill(form.reference ?? 'e2e');
}

test('rejects invalid credentials', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill('alice');
  await page.getByRole('textbox', { name: 'Password' }).fill('incorrect-password');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('alert')).toContainText('Login failed');
});

test('anonymous visitors are redirected to the login page', async ({ page }) => {
  await page.goto('/payments');

  await expect(page).toHaveURL(/\/login$/);
});

test('signing out ends the session and protects the pages again', async ({ page }) => {
  await signIn(page, 'alice', 'Alice123!');

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/accounts');

  await expect(page).toHaveURL(/\/login$/);
});

test('alice can log in, pay, and see the completed transfer and updated balance', async ({ page }) => {
  const reference = `e2e-${Date.now()}`;
  await signIn(page, 'alice', 'Alice123!');
  await expect(page.getByRole('row')).toHaveCount(3);
  await expect(page.getByText('Alice Kask').first()).toBeVisible();

  await openPaymentForm(page);
  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00', reference });
  await page.getByRole('button', { name: 'Send payment' }).click();
  await expect(page).toHaveURL(/\/accounts$/);

  await page.getByRole('link', { name: 'Activity', exact: true }).click();
  const paymentRow = page.getByRole('row').filter({ hasText: reference });
  await expect(paymentRow).toContainText('−1.00 EUR');
  await expect(paymentRow).toContainText('Bob Tamm');
  await paymentRow.click();
  const details = page.getByRole('dialog');
  await expect(details).toContainText('Alice Kask');
  await expect(details).toContainText('Bob Tamm');
  await expect(details).toContainText(BOB_IBAN);
  await details.getByRole('button', { name: 'Close' }).click();

  // The date filter works on the user's own calendar day, in the browser's time zone.
  const today = new Date().toLocaleDateString('sv-SE');
  await page.getByLabel('From date').fill(today);
  await page.getByLabel('To date').fill(today);
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(paymentRow).toBeVisible();
  await page.getByLabel('To date').fill('2000-01-01');
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(page.getByText('No payments match these filters.')).toBeVisible();

  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toContainText('999.00 EUR');
});

test('the payment form blocks submission until required fields are valid', async ({ page }) => {
  await signIn(page, 'alice', 'Alice123!');
  await openPaymentForm(page);

  const submit = page.getByRole('button', { name: 'Send payment' });
  await expect(submit).toBeDisabled();
  for (const label of ['Beneficiary name', 'Beneficiary IBAN']) {
    await page.getByRole('textbox', { name: label }).focus();
    await page.getByRole('textbox', { name: label }).blur();
  }
  await page.getByRole('spinbutton', { name: 'Amount' }).focus();
  await page.getByRole('spinbutton', { name: 'Amount' }).blur();
  await expect(page.getByText('Enter the beneficiary name')).toBeVisible();
  await expect(page.getByText('Enter the beneficiary IBAN')).toBeVisible();
  await expect(page.getByText('Amount must be at least 0.01')).toBeVisible();

  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).fill('LT131000011101001000');
  await page.getByRole('textbox', { name: 'Beneficiary IBAN' }).blur();
  await expect(page.getByText('Enter a valid IBAN')).toBeVisible();
  await expect(submit).toBeDisabled();
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('the app runs without Content Security Policy violations', async ({ page }) => {
  const violations: string[] = [];
  page.on('console', (message) => {
    if (message.text().includes('Content Security Policy')) violations.push(message.text());
  });

  await signIn(page, 'alice', 'Alice123!');
  await page.getByRole('link', { name: 'Activity', exact: true }).click();
  await expect(page).toHaveURL(/\/transactions$/);

  expect(violations).toEqual([]);
});

test('a beneficiary name that does not match the account holder warns the payer, who can send anyway', async ({
  page,
}) => {
  await signIn(page, 'alice', 'Alice123!');
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Mallory Evil', toIban: BOB_IBAN, amount: '1.00' });
  await page.getByRole('button', { name: 'Send payment' }).click();

  const dialog = page.getByRole('dialog', { name: 'Beneficiary name does not match' });
  await expect(dialog).toContainText('Mallory Evil');
  await dialog.getByRole('button', { name: 'Send anyway' }).click();
  await expect(page).toHaveURL(/\/accounts$/);
});

test('a payment larger than the balance is rejected', async ({ page }) => {
  await signIn(page, 'alice', 'Alice123!');
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '999999.00' });
  await page.getByRole('button', { name: 'Send payment' }).click();

  await expect(page.getByRole('alert')).toContainText('Balance is too low');
});

test('the layout stays usable on a phone-sized screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, 'bob', 'Bob12345!');

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await expect(page.getByText('Bob Tamm').first()).toBeVisible();
});
