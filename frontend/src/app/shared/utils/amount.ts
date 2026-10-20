import { AbstractControl, ValidationErrors } from '@angular/forms';

const AMOUNT_PATTERN = /^\d{1,12}(?:[.,]\d{1,2})?$/;

export function parseAmount(text: string): number | null {
  const trimmed = text.trim();
  return AMOUNT_PATTERN.test(trimmed) ? Number(trimmed.replace(',', '.')) : null;
}

export function amountValidator(control: AbstractControl<string>): ValidationErrors | null {
  const text = control.value;
  if (!text) return null;
  const amount = parseAmount(text);
  if (amount === null) return { amount: true };
  return amount === 0 ? { min: true } : null;
}
