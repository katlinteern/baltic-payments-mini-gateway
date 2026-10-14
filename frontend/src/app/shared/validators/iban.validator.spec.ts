import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { ibanValidator } from './iban.validator';

describe('ibanValidator', () => {
  it.each([
    'LT121000011101001000',
    'EE382200221020145685',
    'LV80BANK0000435195001',
  ])('accepts valid IBAN %s', iban => {
    expect(ibanValidator(new FormControl(iban))).toBeNull();
  });

  it('normalizes whitespace and lowercase before checksum validation', () => {
    expect(ibanValidator(new FormControl('ee38 2200 2210 2014 5685'))).toBeNull();
  });

  it.each(['not-an-iban', 'LT131000011101001000'])('rejects malformed or invalid-checksum IBAN %s', iban => {
    expect(ibanValidator(new FormControl(iban))).toEqual({ iban: true });
  });

  it.each([null, ''])('lets required validation handle empty input %s', iban => {
    expect(ibanValidator(new FormControl(iban))).toBeNull();
  });
});