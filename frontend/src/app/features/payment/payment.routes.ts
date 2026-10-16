import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { PaymentsEffects } from './state/payments.effects';
import { paymentsReducer } from './state/payments.reducer';
import { PaymentComponent } from './pages/payment.component';

export const PAYMENT_ROUTES: Routes = [
  {
    path: '',
    component: PaymentComponent,
    providers: [provideState('payments', paymentsReducer), provideEffects(PaymentsEffects)],
  },
];
