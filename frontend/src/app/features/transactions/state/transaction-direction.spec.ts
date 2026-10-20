import { describe, expect, it } from 'vitest';
import { directionOf } from './transaction-direction';

const own = new Set(['LT121000011101001000', 'LV80BANK0000435195001']);

describe('directionOf', () => {
  it('classifies a payment from the point of view of the user', () => {
    expect(directionOf({ fromIban: 'LT121000011101001000', toIban: 'EE382200221020145685' }, own)).toBe('outgoing');
    expect(directionOf({ fromIban: 'EE382200221020145685', toIban: 'LV80BANK0000435195001' }, own)).toBe('incoming');
    expect(directionOf({ fromIban: 'LT121000011101001000', toIban: 'LV80BANK0000435195001' }, own)).toBe('internal');
    expect(directionOf({ fromIban: 'EE382200221020145685', toIban: 'LT601010012345678901' }, own)).toBe('other');
  });
});
