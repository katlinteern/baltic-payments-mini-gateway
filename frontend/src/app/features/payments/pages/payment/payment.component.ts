import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Store } from '@ngrx/store';
import { Account } from '@shared/models/account.model';
import { AlertComponent, PageComponent, SectionHeadComponent, StepperComponent } from '@shared/ui';
import { amountValidator, parseAmount } from '@shared/utils/amount';
import { ibanValidator } from '@shared/validators/iban.validator';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import {
  cancelPayment,
  clearPayments,
  createPayment,
  editPayment,
  startConfirmation,
  stopPolling,
} from '@features/payments/state/payments.actions';
import { selectPayment, selectPaymentBusy, selectPaymentError } from '@features/payments/state/payments.selectors';
import { SmartIdDialogComponent } from '@features/payments/pages/smart-id-dialog/smart-id-dialog.component';

export type PaymentView = 'form' | 'review' | 'authenticating' | 'ended';

export const PAYMENT_STEPS = ['Details', 'Review', 'Confirm'] as const;

@Component({
  selector: 'app-payment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlertComponent,
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PageComponent,
    SectionHeadComponent,
    StepperComponent,
  ],
  templateUrl: './payment.component.html',
  styleUrl: './payment.component.scss',
})
export class PaymentComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private smartIdDialog: MatDialogRef<SmartIdDialogComponent, boolean> | null = null;
  readonly steps = PAYMENT_STEPS;
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly busy = this.store.selectSignal(selectPaymentBusy);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly currentPayment = this.store.selectSignal(selectPayment);
  readonly selectedCurrency = signal('EUR');
  private readonly selectedIban = signal('');
  readonly selectedAccount = computed(() => this.accounts().find((account) => account.iban === this.selectedIban()));
  readonly acknowledged = signal(false);
  readonly form = this.fb.nonNullable.group({
    fromIban: ['', Validators.required],
    beneficiaryName: ['', [Validators.required, Validators.maxLength(120)]],
    toIban: ['', [Validators.required, ibanValidator]],
    amount: ['', [Validators.required, amountValidator]],
    reference: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(140)]],
  });

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

  updateCurrency(iban: string): void {
    this.selectedIban.set(iban);
    const account = this.accounts().find((account: Account) => account.iban === iban);
    this.selectedCurrency.set(account?.currency ?? 'EUR');
  }

  submit(): void {
    if (this.form.invalid || this.busy()) return;
    const value = this.form.getRawValue();
    const amount = parseAmount(value.amount);
    if (amount === null) return;
    this.store.dispatch(
      createPayment({
        request: {
          ...value,
          amount,
          toIban: value.toIban.replace(/\s/g, '').toUpperCase(),
          currency: this.selectedCurrency(),
        },
      }),
    );
  }

  confirm(): void {
    const payment = this.currentPayment();
    if (!payment || this.busy()) return;
    if (payment.nameCheck === 'NO_MATCH' && !this.acknowledged()) return;
    this.store.dispatch(startConfirmation({ id: payment.id, acknowledgeNameMismatch: this.acknowledged() }));
  }

  edit(): void {
    const payment = this.currentPayment();
    this.acknowledged.set(false);
    if (payment && (payment.status === 'READY' || payment.status === 'AWAITING_AUTHENTICATION')) {
      this.store.dispatch(cancelPayment({ id: payment.id }));
    } else {
      this.store.dispatch(editPayment());
    }
  }

  retry(): void {
    this.acknowledged.set(false);
    this.submit();
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
