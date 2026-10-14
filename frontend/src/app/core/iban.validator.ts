import { AbstractControl, ValidationErrors } from '@angular/forms';

export function ibanValidator(control: AbstractControl): ValidationErrors | null {
  const iban = String(control.value ?? '').replace(/\s/g, '').toUpperCase();
  if (!iban) return null;
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) return { iban: true };
  let remainder = 0;
  for (const char of iban.slice(4) + iban.slice(0, 4)) {
    const value = char >= 'A' && char <= 'Z' ? String(char.charCodeAt(0) - 55) : char;
    for (const digit of value) remainder = (remainder * 10 + Number(digit)) % 97;
  }
  return remainder === 1 ? null : { iban: true };
}
