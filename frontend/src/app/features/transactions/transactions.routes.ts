import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { TransactionsEffects } from './state/transactions.effects';
import { transactionsReducer } from './state/transactions.reducer';
import { TransactionsComponent } from './pages/transactions.component';

export const TRANSACTIONS_ROUTES: Routes = [
  {
    path: '',
    component: TransactionsComponent,
    providers: [provideState('transactions', transactionsReducer), provideEffects(TransactionsEffects)],
  },
];
