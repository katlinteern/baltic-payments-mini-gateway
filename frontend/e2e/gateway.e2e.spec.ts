import AxeBuilder from '@axe-core/playwright';
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
  await page.getByRole('textbox', { name: 'Amount' }).fill(form.amount);
  await page.getByRole('textbox', { name: 'Payment reference' }).fill(form.reference ?? 'e2e');
}

/** The simulated Smart-ID approves after a few seconds, so waiting for the receipt needs more than the default. */
const APPROVAL_TIMEOUT = 20_000;

async function continueToReview(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('heading', { name: 'Review payment' })).toBeVisible();
}

async function confirmAndWaitForReceipt(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  await expect(page.getByText(/Verification code\s*\d{4}/)).toBeVisible();
  await expect(page).toHaveURL(/\/payments\/[^/]+\/receipt$/, { timeout: APPROVAL_TIMEOUT });
  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible();
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
  await continueToReview(page);
  await expect(page.getByText('The name matches the account holder.')).toBeVisible();
  await expect(page.getByText('0.00 EUR')).toBeVisible();
  await confirmAndWaitForReceipt(page);
  const receipt = page.getByRole('main');
  await expect(receipt).toContainText('1.00 EUR');
  await expect(receipt).toContainText('Bob Tamm');
  await expect(receipt).toContainText(reference);
  await expect(receipt).toContainText('999.00 EUR');

  // The receipt is addressed by the payment, so a refresh shows it again.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible();

  await page.getByRole('link', { name: 'Activity', exact: true }).first().click();
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
  const today = new Date().toLocaleDateString('en-US');
  await page.getByLabel('From date').fill(today);
  await page.getByLabel('To date').fill(today);
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(paymentRow).toBeVisible();
  await page.getByLabel('To date').fill('1/1/2000');
  await page.getByRole('button', { name: 'Filter' }).click();
  await expect(page.getByText('No payments match these filters.')).toBeVisible();

  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toContainText('999.00 EUR');
});

test('the payment form blocks submission until required fields are valid', async ({ page }) => {
  await signIn(page, 'alice', 'Alice123!');
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

test('a beneficiary name that does not match the account holder must be acknowledged before the payer can confirm', async ({
  page,
}) => {
  await signIn(page, 'alice', 'Alice123!');
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
  await signIn(page, 'alice', 'Alice123!');
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
  await signIn(page, 'alice', 'Alice123!');
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);
  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  await expect(page.getByText(/Verification code\s*\d{4}/)).toBeVisible();
  await expect(page.getByText(/Time left \d:\d{2}/)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel' }).click();

  await expect(page.getByRole('heading', { name: 'New payment' })).toBeVisible();
  await expect(page).toHaveURL(/\/payments$/);
});

test('a payment larger than the balance is refused already when it is prepared', async ({ page }) => {
  await signIn(page, 'alice', 'Alice123!');
  await openPaymentForm(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '999999.00' });
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('alert')).toContainText('Balance is too low');
  await expect(page.getByRole('heading', { name: 'New payment' })).toBeVisible();
});

async function expectNoAccessibilityViolations(page: Page): Promise<void> {
  // Axe measures colours at the moment it runs, so let opening animations finish first.
  await page.evaluate(() =>
    Promise.all(
      document
        .getAnimations()
        .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity)
        .map((animation) => animation.finished.catch(() => undefined)),
    ),
  );
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(
    results.violations.map(
      (violation) => `${violation.id}: ${violation.help} ${violation.nodes.map((node) => node.target).join(' ')}`,
    ),
  ).toEqual([]);
}

test('every page and every step of a payment pass automated WCAG 2.1 AA checks', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await signIn(page, 'alice', 'Alice123!');
  await expect(page.getByRole('row').filter({ hasText: ALICE_IBAN })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await page.getByRole('link', { name: 'Activity', exact: true }).click();
  await expect(page).toHaveURL(/\/transactions$/);
  await expectNoAccessibilityViolations(page);

  await openPaymentForm(page);
  await expectNoAccessibilityViolations(page);

  await fillPayment(page, { beneficiaryName: 'Bob Tamm', toIban: BOB_IBAN, amount: '1.00' });
  await continueToReview(page);
  await expectNoAccessibilityViolations(page);

  await page.getByRole('button', { name: 'Confirm with Smart-ID' }).click();
  await expect(page.getByText(/Verification code\s*\d{4}/)).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await expect(page.getByRole('heading', { name: 'Payment completed' })).toBeVisible({ timeout: APPROVAL_TIMEOUT });
  await expectNoAccessibilityViolations(page);
});

test('the layout stays usable on a phone-sized screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, 'bob', 'Bob12345!');

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await expect(page.getByText('Bob Tamm').first()).toBeVisible();
});
