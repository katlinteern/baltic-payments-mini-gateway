import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { AccountsComponent } from './features/accounts/feature/accounts.component';
import { LoginComponent } from './features/login/feature/login.component';
import { PaymentComponent } from './features/payment/feature/payment.component';
import { TransactionsComponent } from './features/transactions/feature/transactions.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'accounts', component: AccountsComponent, canActivate: [authGuard] },
  { path: 'payments', component: PaymentComponent, canActivate: [authGuard] },
  { path: 'transactions', component: TransactionsComponent, canActivate: [authGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'accounts' },
  { path: '**', redirectTo: 'accounts' },
];
