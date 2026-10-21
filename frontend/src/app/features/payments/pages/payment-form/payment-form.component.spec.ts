import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Account } from '@shared/models/account.model';
import { PaymentRequest } from '@features/payments/state/payment.model';
import { createPaymentForm } from './payment-form';
import { PaymentFormComponent } from './payment-form.component';

const SOURCE = 'LT121000011101001000';
const accounts: Account[] = [
  { id: 'eur', iban: SOURCE, currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
  { id: 'usd', iban: 'LT601010012345678901', currency: 'USD', holderName: 'Alice Kask', balance: 50 },
];
const filled = {
  fromIban: SOURCE,
  beneficiaryName: 'Bob',
  toIban: 'EE38 2200 2210 2014 5685',
  amount: '66.00',
  reference: 'rent',
};

function setup(values: Partial<typeof filled> = filled) {
  const form = createPaymentForm();
  form.patchValue(values);
  const fixture = TestBed.createComponent(PaymentFormComponent);
  fixture.componentRef.setInput('form', form);
  fixture.componentRef.setInput('accounts', accounts);
  const submitted = vi.fn<(request: PaymentRequest) => void>();
  fixture.componentInstance.submitted.subscribe(submitted);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  return { fixture, form, component: fixture.componentInstance, submitted, text, element };
}

describe('PaymentFormComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('hands over the exact amount and a normalised IBAN, with the currency of the chosen account', () => {
    const { component, submitted } = setup();

    component.submit();

    expect(submitted).toHaveBeenCalledWith({
      fromIban: SOURCE,
      beneficiaryName: 'Bob',
      toIban: 'EE382200221020145685',
      amount: 66,
      currency: 'EUR',
      reference: 'rent',
    });
  });

  it('reads the amount as typed, whatever decimal separator was used', () => {
    const { component, submitted } = setup({ ...filled, amount: '10,5' });

    component.submit();

    expect(submitted.mock.calls[0][0].amount).toBe(10.5);
  });

  it('follows the currency of another account when the payer picks it', () => {
    const { fixture, form, component, submitted, text } = setup();

    form.controls.fromIban.setValue('LT601010012345678901');
    fixture.detectChanges();
    component.submit();

    expect(text()).toContain('Available: 50.00 USD');
    expect(submitted.mock.calls[0][0].currency).toBe('USD');
  });

  it('shows the account that was already chosen when the form is shown again', () => {
    const { text } = setup();

    expect(text()).toContain('Available: 1,000.00 EUR');
  });

  it('hands over nothing for an invalid form', () => {
    const { component, submitted } = setup({ ...filled, toIban: '' });

    component.submit();

    expect(submitted).not.toHaveBeenCalled();
  });

  it('hands over nothing while another request is in progress', () => {
    const { fixture, component, submitted } = setup();
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();

    component.submit();

    expect(submitted).not.toHaveBeenCalled();
  });

  it('keeps the continue button disabled until the form is valid', () => {
    const { fixture, form, element } = setup({});
    const button = () => element.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button().disabled).toBe(true);

    form.setValue(filled);
    fixture.componentRef.setInput('busy', false);
    fixture.detectChanges();

    expect(button().disabled).toBe(false);
  });

  it('explains what is wrong with a field once the payer has touched it', () => {
    const { fixture, form, text } = setup({ ...filled, toIban: 'LT131000011101001000' });

    form.markAllAsTouched();
    fixture.detectChanges();

    expect(text()).toContain('Enter a valid IBAN');
  });
});
