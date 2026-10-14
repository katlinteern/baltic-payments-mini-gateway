import { describe, expect, it } from 'vitest';
import { Account } from '../../../shared/models/account.model';
import { clearAccounts, loadAccounts, loadAccountsFailure, loadAccountsSuccess } from './accounts.actions';
import { accountsReducer, initialAccountsState } from './accounts.reducer';

describe('accountsReducer', () => {
  it('tracks load, success and failure state', () => {
    const loading = accountsReducer(initialAccountsState, loadAccounts());
    expect(loading.loading).toBe(true);

    const accounts: Account[] = [{ id: 'a1', iban: 'LT121000011101001000', currency: 'EUR', balance: 20 }];
    const loaded = accountsReducer(loading, loadAccountsSuccess({ accounts }));
    expect(loaded).toMatchObject({ items: accounts, loading: false, error: null });

    const failed = accountsReducer(loading, loadAccountsFailure({ error: 'offline' }));
    expect(failed).toMatchObject({ loading: false, error: 'offline' });
  });

  it('clears account state on logout', () => {
    expect(accountsReducer({ ...initialAccountsState, loading: true, error: 'x' }, clearAccounts()))
      .toEqual(initialAccountsState);
  });
});