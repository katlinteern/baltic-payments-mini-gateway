import { Component, inject, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Store } from '@ngrx/store';
import { AlertComponent, PageComponent, StepperComponent } from '@shared/ui';
import { loadIntent } from '../state/payments.actions';
import { PAYMENT_STEPS } from './payment.component';
import { selectIntent, selectPaymentBusy, selectPaymentError } from '../state/payments.selectors';

/** The receipt of an executed payment. It is loaded by id, so it survives a refresh and can be opened again. */
@Component({
  selector: 'app-payment-receipt',
  standalone: true,
  imports: [
    AlertComponent,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    PageComponent,
    StepperComponent,
  ],
  templateUrl: './payment-receipt.component.html',
  styleUrl: './payment-receipt.component.scss',
})
export class PaymentReceiptComponent implements OnInit {
  readonly steps = PAYMENT_STEPS;
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(Store);
  readonly intent = this.store.selectSignal(selectIntent);
  readonly busy = this.store.selectSignal(selectPaymentBusy);
  readonly error = this.store.selectSignal(selectPaymentError);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.store.dispatch(loadIntent({ id }));
  }
}
