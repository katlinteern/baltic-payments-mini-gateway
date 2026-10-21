import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Account } from '@shared/models/account.model';
import { amountValidator, parseAmount } from '@shared/utils/amount';
import { ibanValidator } from '@shared/validators/iban.validator';
import { PaymentRequest } from '@features/payments/state/payment.model';

export function createPaymentForm() {
  return new FormGroup({
    fromIban: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    beneficiaryName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    toIban: new FormControl('', { nonNullable: true, validators: [Validators.required, ibanValidator] }),
    amount: new FormControl('', { nonNullable: true, validators: [Validators.required, amountValidator] }),
    reference: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(140)],
    }),
  });
}

export type PaymentFormGroup = ReturnType<typeof createPaymentForm>;

export function currencyOf(accounts: readonly Account[], iban: string): string {
  return accounts.find((account) => account.iban === iban)?.currency ?? 'EUR';
}

export function toPaymentRequest(form: PaymentFormGroup, accounts: readonly Account[]): PaymentRequest | null {
  if (form.invalid) return null;
  const value = form.getRawValue();
  const amount = parseAmount(value.amount);
  if (amount === null) return null;
  return {
    ...value,
    amount,
    toIban: value.toIban.replace(/\s/g, '').toUpperCase(),
    currency: currencyOf(accounts, value.fromIban),
  };
}
