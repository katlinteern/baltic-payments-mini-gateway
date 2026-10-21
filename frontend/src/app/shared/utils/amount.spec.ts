import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { amountValidator, parseAmount } from './amount';

describe('parseAmount', () => {
  it.each([
    ['10', 10],
    ['10.5', 10.5],
    ['10,5', 10.5],
    [' 007.05 ', 7.05],
    ['0.01', 0.01],
    ['999999999999.99', 999999999999.99],
  ])('reads %s as %s', (text, expected) => {
    expect(parseAmount(text)).toBe(expected);
  });

  it.each(['', 'abc', '-1', '1.234', '1e3', '1.', '.5', '1 000', '1234567890123'])('rejects %s', (text) => {
    expect(parseAmount(text)).toBeNull();
  });

  it('gives back exactly the digits that were typed, up to the supported size', () => {
    for (const text of ['0.1', '0.29', '1234.56', '999999999999.99', '100000000000.01']) {
      expect(parseAmount(text)?.toFixed(2)).toBe(Number(text).toFixed(2));
      expect(String(parseAmount(text))).toBe(String(Number(text)));
    }
  });
});

describe('amountValidator', () => {
  const errors = (text: string) => amountValidator(new FormControl(text, { nonNullable: true }));

  it('accepts a valid amount and leaves empty to the required validator', () => {
    expect(errors('12.34')).toBeNull();
    expect(errors('')).toBeNull();
  });

  it('flags malformed text and zero', () => {
    expect(errors('1.234')).toEqual({ amount: true });
    expect(errors('0')).toEqual({ min: true });
    expect(errors('0,00')).toEqual({ min: true });
  });
});
