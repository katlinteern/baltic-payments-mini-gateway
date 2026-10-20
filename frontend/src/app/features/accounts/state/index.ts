import { makeEnvironmentProviders } from '@angular/core';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { AccountsEffects } from './accounts.effects';
import { accountsReducer } from './accounts.reducer';

/** Public API of the accounts feature: the only entry point other features may import. */
export { loadAccounts } from './accounts.actions';
export { selectAccounts } from './accounts.selectors';

/**
 * The accounts slice is registered once at application start because the payment form also depends on it,
 * unlike the other feature slices, which are registered when their route is first loaded.
 */
export const provideAccountsState = () =>
  makeEnvironmentProviders([provideState('accounts', accountsReducer), provideEffects(AccountsEffects)]);
