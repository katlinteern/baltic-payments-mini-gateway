import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Up to 12 integer digits keep the whole amount within 14 significant digits, which a double holds exactly. */
const AMOUNT_PATTERN = /^\d{1,12}(?:[.,]\d{1,2})?$/;

/**
 * Reads what the payer typed as an amount with at most two decimals ("10,5" is 10.5), or null when it is not one.
 * The text is validated before it becomes a number, so the value sent is exactly what was typed.
 */
export function parseAmount(text: string): number | null {
  const trimmed = text.trim();
  return AMOUNT_PATTERN.test(trimmed) ? Number(trimmed.replace(',', '.')) : null;
}

/** Empty is left to {@code Validators.required}; `amount` means malformed, `min` means zero. */
export function amountValidator(control: AbstractControl<string>): ValidationErrors | null {
  const text = control.value;
  if (!text) return null;
  const amount = parseAmount(text);
  if (amount === null) return { amount: true };
  return amount === 0 ? { min: true } : null;
}
