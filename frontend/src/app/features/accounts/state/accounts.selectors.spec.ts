import { describe, expect, it } from 'vitest';
import { Account } from '@shared/models/account.model';
import { selectEuroTotal } from './accounts.selectors';

const account = (currency: string, balance: number): Account => ({
  id: `${currency}-${balance}`,
  iban: 'LT121000011101001000',
  currency,
  holderName: 'Alice Kask',
  balance,
});

describe('selectEuroTotal', () => {
  it('sums only EUR balances without floating-point drift', () => {
    expect(selectEuroTotal.projector([account('EUR', 0.1), account('EUR', 0.2), account('USD', 50)])).toBe(0.3);
  });

  it('is zero without accounts', () => {
    expect(selectEuroTotal.projector([])).toBe(0);
  });
});
