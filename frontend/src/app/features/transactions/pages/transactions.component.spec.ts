import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';
import { MatDialog } from '@angular/material/dialog';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '@core/auth.service';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import { Account } from '@shared/models/account.model';
import { Transaction } from '@shared/models/transaction.model';
import { loadTransactions } from '../state/transactions.actions';
import {
  selectTransactions,
  selectTransactionsError,
  selectTransactionsLoading,
  selectTransactionsTotal,
} from '../state/transactions.selectors';
import { TransactionDetailDialogComponent } from './transaction-detail-dialog.component';
import { TransactionsComponent } from './transactions.component';

const ALICE = 'LT121000011101001000';
const BOB = 'EE382200221020145685';

const account: Account = { id: '1', iban: ALICE, currency: 'EUR', holderName: 'Alice Kask', balance: 100 };
const transaction = (fromIban: string, toIban: string, amount: number): Transaction => ({
  id: `${fromIban}-${toIban}`,
  fromIban,
  toIban,
  fromName: fromIban === ALICE ? 'Alice Kask' : 'Bob Tamm',
  toName: toIban === ALICE ? 'Alice Kask' : 'Bob Tamm',
  beneficiaryName: 'Bob Tamm',
  nameCheck: 'MATCH',
  amount,
  currency: 'EUR',
  reference: null,
  status: 'COMPLETED',
  fromBalanceAfter: fromIban === ALICE ? 75 : null,
  toBalanceAfter: toIban === ALICE ? 75 : null,
  createdAt: '2026-03-11T10:00:00Z',
});

function render(transactions: Transaction[], role = 'USER', accounts: Account[] = [account]) {
  const dispatch = vi.fn();
  const open = vi.fn();
  const values = new Map<unknown, unknown>([
    [selectTransactions, signal(transactions)],
    [selectTransactionsTotal, signal(transactions.length)],
    [selectTransactionsLoading, signal(false)],
    [selectTransactionsError, signal(null)],
    [selectAccounts, signal(accounts)],
  ]);
  TestBed.configureTestingModule({
    imports: [TransactionsComponent],
    providers: [
      { provide: MatDialog, useValue: { open } },
      { provide: AuthService, useValue: { role: () => role } },
      { provide: Store, useValue: { dispatch, selectSignal: (selector: unknown) => values.get(selector) } },
    ],
  });
  const fixture = TestBed.createComponent(TransactionsComponent);
  fixture.detectChanges();
  return { dispatch, open, fixture, element: fixture.nativeElement as HTMLElement };
}

const amountCells = (element: HTMLElement) =>
  Array.from(element.querySelectorAll('td.balance-cell')).map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim());

describe('TransactionsComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads the accounts and the first page of transactions on init', () => {
    const { dispatch } = render([]);

    expect(dispatch).toHaveBeenCalledWith(loadAccounts());
    expect(dispatch).toHaveBeenCalledWith(
      loadTransactions({ filters: { iban: '', dateFrom: '', dateTo: '', page: 0, size: 10 } }),
    );
  });

  it('marks money leaving the user as negative and money arriving as positive', () => {
    const { element } = render([transaction(ALICE, BOB, 25), transaction(BOB, ALICE, 40)]);

    expect(amountCells(element)).toEqual(['−25.00 EUR', '+40.00 EUR']);
    expect(element.querySelectorAll('td.incoming')).toHaveLength(1);
  });

  it('colours outgoing amounts red and incoming amounts green', () => {
    const { element } = render([transaction(ALICE, BOB, 25), transaction(BOB, ALICE, 40)]);

    expect(element.querySelectorAll('td.outgoing')).toHaveLength(1);
    expect(element.querySelectorAll('td.incoming')).toHaveLength(1);
  });

  it('shows the date only, in Estonian format, and the counterparty name', () => {
    const { element } = render([transaction(ALICE, BOB, 25), transaction(BOB, ALICE, 40)]);
    const text = element.textContent ?? '';

    expect(text).toContain('11.03.2026');
    expect(element.querySelector('.date-time')?.textContent?.trim()).toMatch(/^\d\d:\d\d$/);
    expect(element.querySelectorAll('.party-text')[0].textContent?.trim()).toBe('Bob Tamm');
    expect(element.querySelectorAll('.party-text')[1].textContent?.trim()).toBe('Bob Tamm');
  });

  it('shows the balance after the payment instead of the status', () => {
    const { element } = render([transaction(ALICE, BOB, 25)]);

    expect(element.querySelector('td.balance-after')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('75.00 EUR');
    expect(element.textContent).not.toContain('COMPLETED');
  });

  it('opens the payment details when a row is clicked', () => {
    const { element, open } = render([transaction(ALICE, BOB, 25)]);

    (element.querySelector('tr.mat-mdc-row') as HTMLElement).click();

    expect(open).toHaveBeenCalledWith(
      TransactionDetailDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ direction: 'outgoing' }) }),
    );
  });

  it("lists a transfer between the user's own accounts twice, as a debit and a credit", () => {
    const other: Account = { id: '2', iban: BOB, currency: 'EUR', holderName: 'Alice Kask', balance: 5 };
    const { element } = render([transaction(ALICE, BOB, 25)], 'USER', [account, other]);

    expect(amountCells(element)).toEqual(['−25.00 EUR', '+25.00 EUR']);
  });

  it('shows no direction to an auditor, who is not a party to the payments', () => {
    const { element } = render([transaction(ALICE, BOB, 25)], 'AUDITOR');

    expect(amountCells(element)).toEqual(['25.00 EUR']);
  });

  it('shows an empty state when nothing matches', () => {
    const { element } = render([]);

    expect(element.textContent).toContain('No payments match these filters.');
  });
});
