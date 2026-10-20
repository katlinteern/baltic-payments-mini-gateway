import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { afterEach, describe, expect, it } from 'vitest';
import { Transaction } from '@shared/models/transaction.model';
import { TransactionDetailDialogComponent, TransactionDetailDialogData } from './transaction-detail-dialog.component';

const transaction: Transaction = {
  id: 'tx-1',
  fromIban: 'LT121000011101001000',
  toIban: 'EE382200221020145685',
  fromName: 'Alice Kask',
  toName: 'Bob Tamm',
  beneficiaryName: 'Bob',
  nameCheck: 'CLOSE_MATCH',
  amount: 25.5,
  currency: 'EUR',
  reference: 'Rent',
  status: 'COMPLETED',
  fromBalanceAfter: 74.5,
  toBalanceAfter: null,
  createdAt: '2026-03-11T10:00:00Z',
};

function render(overrides: Partial<TransactionDetailDialogData> = {}, changes: Partial<Transaction> = {}) {
  const data: TransactionDetailDialogData = {
    transaction: { ...transaction, ...changes },
    direction: 'outgoing',
    balanceAfter: 74.5,
    ...overrides,
  };
  TestBed.configureTestingModule({
    imports: [TransactionDetailDialogComponent],
    providers: [{ provide: MAT_DIALOG_DATA, useValue: data }],
  });
  const fixture = TestBed.createComponent(TransactionDetailDialogComponent);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  const text = () => element.textContent?.replace(/\s+/g, ' ') ?? '';
  const rows = () =>
    Object.fromEntries(
      [...element.querySelectorAll('dt')].map((term) => {
        const value = term.nextElementSibling as HTMLElement;
        const parts = value.children.length ? [...value.children] : [value];
        return [term.textContent?.trim(), parts.map((part) => part.textContent?.trim()).join(' ')];
      }),
    );
  return { text, rows };
}

describe('TransactionDetailDialogComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the amount, both parties with their IBANs and the reference', () => {
    const { text, rows } = render();

    expect(text()).toContain('Payment details');
    expect(rows()).toMatchObject({
      Amount: '25.50 EUR',
      From: 'Alice Kask LT121000011101001000',
      To: 'Bob Tamm EE382200221020145685',
      Reference: 'Rent',
    });
  });

  it('shows the balance after the payment when it is known', () => {
    const { rows } = render({ balanceAfter: 74.5 });

    expect(rows()['Balance after']).toBe('74.50 EUR');
  });

  it('leaves the balance out when it is not known', () => {
    const { rows } = render({ balanceAfter: null });

    expect(rows()).not.toHaveProperty('Balance after');
  });

  it('falls back to the typed beneficiary name and a dash when names and reference are missing', () => {
    const { rows } = render({}, { toName: null, reference: null });

    expect(rows().To).toBe('Bob EE382200221020145685');
    expect(rows().Reference).toBe('—');
  });
});
