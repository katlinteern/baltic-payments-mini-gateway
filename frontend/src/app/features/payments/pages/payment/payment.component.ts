import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Store } from '@ngrx/store';
import { AlertComponent, PageComponent, SectionHeadComponent, StepperComponent } from '@shared/ui';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  startConfirmation,
  stopPolling,
} from '@features/payments/state/payments.actions';
import { PaymentRequest } from '@features/payments/state/payment.model';
import { selectPayment, selectPaymentBusy, selectPaymentError } from '@features/payments/state/payments.selectors';
import { createPaymentForm, toPaymentRequest } from '@features/payments/pages/payment-form/payment-form';
import { PaymentFormComponent } from '@features/payments/pages/payment-form/payment-form.component';
import { PaymentReviewComponent } from '@features/payments/pages/payment-review/payment-review.component';
import { SmartIdDialogComponent } from '@features/payments/pages/smart-id-dialog/smart-id-dialog.component';

export type PaymentView = 'form' | 'review' | 'authenticating' | 'ended';

export const PAYMENT_STEPS = ['Details', 'Review', 'Confirm'] as const;

@Component({
  selector: 'app-payment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlertComponent,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    PageComponent,
    PaymentFormComponent,
    PaymentReviewComponent,
    SectionHeadComponent,
    StepperComponent,
  ],
  templateUrl: './payment.component.html',
  styleUrl: './payment.component.scss',
})
export class PaymentComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private smartIdDialog: MatDialogRef<SmartIdDialogComponent, boolean> | null = null;
  readonly steps = PAYMENT_STEPS;
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly busy = this.store.selectSignal(selectPaymentBusy);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly currentPayment = this.store.selectSignal(selectPayment);
  readonly form = createPaymentForm();

  readonly view = computed<PaymentView>(() => {
    switch (this.currentPayment()?.status) {
      case undefined:
        return 'form';
      case 'READY':
        return 'review';
      case 'AWAITING_AUTHENTICATION':
        return 'authenticating';
      default:
        return 'ended';
    }
  });
  readonly step = computed(() => ({ form: 0, review: 1, authenticating: 2, ended: 2 })[this.view()]);
  readonly screen = computed<Exclude<PaymentView, 'authenticating'>>(() => {
    const view = this.view();
    return view === 'authenticating' ? 'review' : view;
  });
  readonly waiting = computed(() => this.view() === 'authenticating');
  readonly endedHeading = computed(() =>
    this.currentPayment()?.status === 'COMPLETED' ? 'Payment' : 'Payment not completed',
  );
  readonly endedMessage = computed(() => {
    const payment = this.currentPayment();
    switch (payment?.status) {
      case 'COMPLETED':
        return 'Payment completed. Opening your receipt…';
      case 'FAILED':
        return payment.failureMessage ?? 'The payment was not completed.';
      case 'EXPIRED':
        return 'The payment request expired before it was confirmed.';
      default:
        return 'The payment was cancelled.';
    }
  });

  constructor() {
    effect(() => {
      const waiting = this.waiting();
      untracked(() => (waiting ? this.openSmartIdDialog() : this.closeSmartIdDialog()));
    });
  }

  ngOnInit(): void {
    this.store.dispatch(clearPayments());
    this.store.dispatch(loadAccounts());
  }

  ngOnDestroy(): void {
    this.closeSmartIdDialog();
    this.store.dispatch(stopPolling());
  }

  submit(request: PaymentRequest): void {
    this.store.dispatch(createPayment({ request }));
  }

  confirm(acknowledgeNameMismatch: boolean): void {
    const payment = this.currentPayment();
    if (!payment || this.busy()) return;
    this.store.dispatch(startConfirmation({ id: payment.id, acknowledgeNameMismatch }));
  }

  edit(): void {
    const payment = this.currentPayment();
    if (payment && (payment.status === 'READY' || payment.status === 'AWAITING_AUTHENTICATION')) {
      this.store.dispatch(cancelPayment({ id: payment.id }));
    } else {
      this.store.dispatch(editPayment());
    }
  }

  retry(): void {
    if (this.busy()) return;
    const request = toPaymentRequest(this.form, this.accounts());
    if (request) this.submit(request);
  }

  private openSmartIdDialog(): void {
    if (this.smartIdDialog) return;
    const dialog = this.dialog.open(SmartIdDialogComponent, {
      autoFocus: 'dialog',
      disableClose: true,
      width: '440px',
      maxWidth: '95vw',
    });
    this.smartIdDialog = dialog;
    dialog.afterClosed().subscribe((cancelled) => {
      this.smartIdDialog = null;
      if (cancelled) this.edit();
    });
  }

  private closeSmartIdDialog(): void {
    this.smartIdDialog?.close();
  }
}
