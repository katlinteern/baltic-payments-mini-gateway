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
import { uuid } from '@shared/utils/uuid';
import { ibanValidator } from '@shared/validators/iban.validator';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import { clearPayments, submitPayment } from '../state/payments.actions';
import { selectCompletedTransaction, selectPaymentError, selectPaymentSubmitting } from '../state/payments.selectors';

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
  readonly submitting = this.store.selectSignal(selectPaymentSubmitting);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly completedTransaction = this.store.selectSignal(selectCompletedTransaction);
  readonly selectedCurrency = signal('EUR');
  readonly form = this.fb.nonNullable.group({
    fromIban: ['', Validators.required],
    beneficiaryName: ['', [Validators.required, Validators.maxLength(120)]],
    toIban: ['', [Validators.required, ibanValidator]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    reference: ['', Validators.maxLength(140)],
  });
  private idempotencyKey = uuid();
  private handledTransactionId: string | null = null;

  constructor() {
    effect(() => {
      const transaction = this.completedTransaction();
      if (transaction && transaction.id !== this.handledTransactionId) {
        this.handledTransactionId = transaction.id;
        this.idempotencyKey = uuid();
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

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    this.store.dispatch(
      submitPayment({
        request: { ...value, toIban: value.toIban.replace(/\s/g, '').toUpperCase(), currency: this.selectedCurrency() },
        idempotencyKey: this.idempotencyKey,
      }),
    );
  }
}
