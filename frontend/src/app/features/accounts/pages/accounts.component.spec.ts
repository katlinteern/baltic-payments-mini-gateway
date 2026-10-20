import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Store } from '@ngrx/store';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '@core/auth.service';
import { Account } from '@shared/models/account.model';
import { loadAccounts } from '../state/accounts.actions';
import {
  selectAccounts,
  selectAccountsError,
  selectAccountsLoading,
  selectEuroTotal,
} from '../state/accounts.selectors';
import { AccountsComponent } from './accounts.component';

function render(accounts: Account[], error: string | null = null) {
  const dispatch = vi.fn();
  const values = new Map<unknown, unknown>([
    [selectAccounts, signal(accounts)],
    [selectEuroTotal, signal(accounts.reduce((sum, account) => sum + account.balance, 0))],
    [selectAccountsLoading, signal(false)],
    [selectAccountsError, signal(error)],
  ]);
  TestBed.configureTestingModule({
    imports: [AccountsComponent],
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { username: () => 'alice' } },
      { provide: Store, useValue: { dispatch, selectSignal: (selector: unknown) => values.get(selector) } },
    ],
  });
  const fixture = TestBed.createComponent(AccountsComponent);
  fixture.detectChanges();
  return { dispatch, element: fixture.nativeElement as HTMLElement };
}

describe('AccountsComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads accounts on init and renders IBAN, holder and balance', () => {
    const { dispatch, element } = render([
      { id: '1', iban: 'LT121000011101001000', currency: 'EUR', holderName: 'Alice Kask', balance: 1000 },
    ]);

    expect(dispatch).toHaveBeenCalledWith(loadAccounts());
    expect(element.textContent).toContain('LT121000011101001000');
    expect(element.textContent).toContain('Alice Kask');
    expect(element.textContent).toContain('1,000.00');
  });

  it('shows an empty state when the user has no accounts', () => {
    const { element } = render([]);

    expect(element.textContent).toContain('No accounts are linked to this user.');
  });

  it('announces load errors to assistive technology', () => {
    const { element } = render([], 'Could not load accounts.');

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load accounts.');
  });
});
