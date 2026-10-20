import { makeEnvironmentProviders } from '@angular/core';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { AccountsEffects } from './accounts.effects';
import { accountsReducer } from './accounts.reducer';

export { loadAccounts } from './accounts.actions';
export { selectAccounts } from './accounts.selectors';

export const provideAccountsState = () =>
  makeEnvironmentProviders([provideState('accounts', accountsReducer), provideEffects(AccountsEffects)]);
