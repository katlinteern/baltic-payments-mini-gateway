import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { PaymentReceiptComponent } from './pages/payment-receipt/payment-receipt.component';
import { PaymentComponent } from './pages/payment/payment.component';
import { PaymentsEffects } from './state/payments.effects';
import { paymentsReducer } from './state/payments.reducer';

export const PAYMENTS_ROUTES: Routes = [
  {
    path: '',
    providers: [provideState('payments', paymentsReducer), provideEffects(PaymentsEffects)],
    children: [
      { path: '', component: PaymentComponent },
      { path: ':id/receipt', component: PaymentReceiptComponent },
    ],
  },
];
