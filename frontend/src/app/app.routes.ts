import { Routes } from '@angular/router';
import { authGuard } from '@core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadChildren: () => import('./features/login/login.routes').then((m) => m.LOGIN_ROUTES),
  },
  {
    path: 'accounts',
    canActivate: [authGuard],
    loadChildren: () => import('./features/accounts/accounts.routes').then((m) => m.ACCOUNTS_ROUTES),
  },
  {
    path: 'payments',
    canActivate: [authGuard],
    loadChildren: () => import('./features/payments/payments.routes').then((m) => m.PAYMENTS_ROUTES),
  },
  {
    path: 'transactions',
    canActivate: [authGuard],
    loadChildren: () => import('./features/transactions/transactions.routes').then((m) => m.TRANSACTIONS_ROUTES),
  },
  { path: '', pathMatch: 'full', redirectTo: 'accounts' },
  { path: '**', redirectTo: 'accounts' },
];
