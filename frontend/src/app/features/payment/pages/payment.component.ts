import { Component, effect, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Store } from '@ngrx/store';
import { Account } from '@shared/models/account.model';
import { AlertComponent, PageComponent, SectionHeadComponent } from '@shared/ui';
import { ibanValidator } from '@shared/validators/iban.validator';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import { IdempotencyKeys } from '../state/idempotency-keys';
import { PaymentRequest } from '../state/payment-request.model';
import { clearPayments, editPayment, submitPayment, verifyPayee } from '../state/payments.actions';
import {
  selectCompletedTransaction,
  selectPayeeVerification,
  selectPayeeVerifying,
  selectPaymentError,
  selectPaymentSubmitting,
} from '../state/payments.selectors';

@Component({
  selector: 'app-payment',
  standalone: true,
  imports: [
    AlertComponent,
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
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
export class PaymentComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly verifying = this.store.selectSignal(selectPayeeVerifying);
  readonly verification = this.store.selectSignal(selectPayeeVerification);
  readonly submitting = this.store.selectSignal(selectPaymentSubmitting);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly completedTransaction = this.store.selectSignal(selectCompletedTransaction);
  readonly selectedCurrency = signal('EUR');
  /** The payment exactly as it was verified; confirming sends this, not whatever the form holds later. */
  readonly reviewed = signal<PaymentRequest | null>(null);
  readonly form = this.fb.nonNullable.group({
    fromIban: ['', Validators.required],
    beneficiaryName: ['', [Validators.required, Validators.maxLength(120)]],
    toIban: ['', [Validators.required, ibanValidator]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    reference: ['', Validators.maxLength(140)],
  });
  private readonly idempotency = new IdempotencyKeys();
  private handledTransactionId: string | null = null;

  constructor() {
    effect(() => {
      const transaction = this.completedTransaction();
      if (transaction && transaction.id !== this.handledTransactionId) {
        this.handledTransactionId = transaction.id;
        this.idempotency.renew();
        this.reviewed.set(null);
        this.form.patchValue({ beneficiaryName: '', toIban: '', amount: 0, reference: '' });
        this.form.markAsPristine();
      }
    });
  }

  ngOnInit(): void {
    this.store.dispatch(clearPayments());
    this.store.dispatch(loadAccounts());
  }

  updateCurrency(iban: string): void {
    const account = this.accounts().find((account: Account) => account.iban === iban);
    this.selectedCurrency.set(account?.currency ?? 'EUR');
  }

  /** Step 1: check the beneficiary name against the account holder before any money moves. */
  review(): void {
    if (this.form.invalid || this.verifying() || this.submitting()) return;
    const value = this.form.getRawValue();
    const request: PaymentRequest = {
      ...value,
      toIban: value.toIban.replace(/\s/g, '').toUpperCase(),
      currency: this.selectedCurrency(),
    };
    this.reviewed.set(request);
    this.store.dispatch(verifyPayee({ toIban: request.toIban, beneficiaryName: request.beneficiaryName }));
  }

  edit(): void {
    this.store.dispatch(editPayment());
  }

  /** Step 2: the payer has seen the verification result and confirms; a mismatch must be acknowledged explicitly. */
  confirm(): void {
    const verification = this.verification();
    const reviewed = this.reviewed();
    if (!verification || !reviewed || this.submitting()) return;
    const request = verification.result === 'NO_MATCH' ? { ...reviewed, proceedDespiteNameMismatch: true } : reviewed;
    this.store.dispatch(submitPayment({ request, idempotencyKey: this.idempotency.keyFor(request) }));
  }
}
