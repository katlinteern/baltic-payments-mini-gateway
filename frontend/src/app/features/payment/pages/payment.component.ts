import { Component, computed, DestroyRef, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Store } from '@ngrx/store';
import { Account } from '@shared/models/account.model';
import { AlertComponent, PageComponent, SectionHeadComponent } from '@shared/ui';
import { amountValidator, parseAmount } from '@shared/utils/amount';
import { ibanValidator } from '@shared/validators/iban.validator';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import {
  cancelIntent,
  clearPayments,
  createIntent,
  editPayment,
  startConfirmation,
  stopPolling,
} from '../state/payments.actions';
import { selectIntent, selectPaymentBusy, selectPaymentError } from '../state/payments.selectors';

/** The payment screen: one page that moves from the form through review and confirmation to a receipt. */
export type PaymentView = 'form' | 'review' | 'authenticating' | 'ended';

@Component({
  selector: 'app-payment',
  standalone: true,
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
  ],
  templateUrl: './payment.component.html',
  styleUrl: './payment.component.scss',
})
export class PaymentComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly busy = this.store.selectSignal(selectPaymentBusy);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly intent = this.store.selectSignal(selectIntent);
  readonly selectedCurrency = signal('EUR');
  private readonly selectedIban = signal('');
  readonly selectedAccount = computed(() => this.accounts().find((account) => account.iban === this.selectedIban()));
  /** The payer accepted the risk of paying a beneficiary whose name does not match the account holder. */
  readonly acknowledged = signal(false);
  private readonly now = signal(Date.now());
  readonly form = this.fb.nonNullable.group({
    fromIban: ['', Validators.required],
    beneficiaryName: ['', [Validators.required, Validators.maxLength(120)]],
    toIban: ['', [Validators.required, ibanValidator]],
    amount: ['', [Validators.required, amountValidator]],
    reference: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(140)]],
  });

  readonly view = computed<PaymentView>(() => {
    switch (this.intent()?.status) {
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
  /** Time the payer has left to approve, as m:ss. */
  readonly countdown = computed(() => {
    const expiresAt = this.intent()?.expiresAt;
    const seconds = expiresAt ? Math.max(0, Math.ceil((Date.parse(expiresAt) - this.now()) / 1000)) : 0;
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });
  readonly endedMessage = computed(() => {
    const intent = this.intent();
    switch (intent?.status) {
      case 'COMPLETED':
        return 'Payment completed. Opening your receipt…';
      case 'FAILED':
        return intent.failureMessage ?? 'The payment was not completed.';
      case 'EXPIRED':
        return 'The payment request expired before it was confirmed.';
      default:
        return 'The payment was cancelled.';
    }
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit(): void {
    this.store.dispatch(clearPayments());
    this.store.dispatch(loadAccounts());
  }

  ngOnDestroy(): void {
    this.store.dispatch(stopPolling());
  }

  updateCurrency(iban: string): void {
    this.selectedIban.set(iban);
    const account = this.accounts().find((account: Account) => account.iban === iban);
    this.selectedCurrency.set(account?.currency ?? 'EUR');
  }

  /** Prepares the payment: the server checks the accounts and the beneficiary name, and nothing is sent yet. */
  submit(): void {
    if (this.form.invalid || this.busy()) return;
    const value = this.form.getRawValue();
    const amount = parseAmount(value.amount);
    if (amount === null) return;
    this.store.dispatch(
      createIntent({
        request: {
          ...value,
          amount,
          toIban: value.toIban.replace(/\s/g, '').toUpperCase(),
          currency: this.selectedCurrency(),
        },
      }),
    );
  }

  /** The payer reviewed the payment; the authentication app is asked to approve it. */
  confirm(): void {
    const intent = this.intent();
    if (!intent || this.busy()) return;
    if (intent.nameCheck === 'NO_MATCH' && !this.acknowledged()) return;
    this.store.dispatch(startConfirmation({ id: intent.id, acknowledgeNameMismatch: this.acknowledged() }));
  }

  /** Withdraws the prepared payment and returns to the form, which still holds what was typed. */
  edit(): void {
    const intent = this.intent();
    this.acknowledged.set(false);
    if (intent && (intent.status === 'READY' || intent.status === 'AWAITING_AUTHENTICATION')) {
      this.store.dispatch(cancelIntent({ id: intent.id }));
    } else {
      this.store.dispatch(editPayment());
    }
  }
}
