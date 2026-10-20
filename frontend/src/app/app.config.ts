import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideStore } from '@ngrx/store';
import { authInterceptor } from '@core/auth.interceptor';
import { clearStateOnSessionEnd } from '@core/session';
import { provideAccountsState } from '@features/accounts/state';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideAnimations(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    // Feature slices (login, payments, transactions) register themselves when their route is first loaded.
    provideStore({}, { metaReducers: [clearStateOnSessionEnd] }),
    provideEffects(),
    provideAccountsState(),
  ],
};
