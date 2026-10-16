import { ApplicationConfig, isDevMode, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideEffects } from '@ngrx/effects';
import { provideStore } from '@ngrx/store';
import { authInterceptor } from './core/auth.interceptor';
import { routes } from './app.routes';
import { accountsReducer } from './features/accounts/state/accounts.reducer';
import { AccountsEffects } from './features/accounts/state/accounts.effects';
import { loginReducer } from './features/login/state/login.reducer';
import { LoginEffects } from './features/login/state/login.effects';
import { paymentsReducer } from './features/payment/state/payments.reducer';
import { PaymentsEffects } from './features/payment/state/payments.effects';
import { transactionsReducer } from './features/transactions/state/transactions.reducer';
import { TransactionsEffects } from './features/transactions/state/transactions.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideAnimations(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    provideStore({
      accounts: accountsReducer,
      login: loginReducer,
      payments: paymentsReducer,
      transactions: transactionsReducer,
    }),
    provideEffects([AccountsEffects, LoginEffects, PaymentsEffects, TransactionsEffects]),
    // Caches only the static app shell; API calls and authenticated data are never cached by the worker.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
