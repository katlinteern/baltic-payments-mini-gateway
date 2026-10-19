import { Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
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
import { NameMismatchDialogComponent } from './name-mismatch-dialog.component';
import { IdempotencyKeys } from '../state/idempotency-keys';
import { PaymentRequest } from '../state/payment-request.model';
import { clearPayments, submitPayment } from '../state/payments.actions';
import {
  selectCompletedTransaction,
  selectNameMismatch,
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
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly store = inject(Store);
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly submitting = this.store.selectSignal(selectPaymentSubmitting);
  readonly error = this.store.selectSignal(selectPaymentError);
  readonly nameMismatch = this.store.selectSignal(selectNameMismatch);
  readonly completedTransaction = this.store.selectSignal(selectCompletedTransaction);
  readonly selectedCurrency = signal('EUR');
  private readonly selectedIban = signal('');
  readonly selectedAccount = computed(() => this.accounts().find((account) => account.iban === this.selectedIban()));
  /** The last submitted payment, so a rejected name mismatch can be sent again once the payer acknowledges it. */
  private lastRequest: PaymentRequest | null = null;
  readonly form = this.fb.nonNullable.group({
    fromIban: ['', Validators.required],
    beneficiaryName: ['', [Validators.required, Validators.maxLength(120)]],
    toIban: ['', [Validators.required, ibanValidator]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    reference: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(140)]],
  });
  private readonly idempotency = new IdempotencyKeys();
  private handledTransactionId: string | null = null;

  constructor() {
    effect(() => {
      const transaction = this.completedTransaction();
      if (transaction && transaction.id !== this.handledTransactionId) {
        this.handledTransactionId = transaction.id;
        this.idempotency.renew();
        this.lastRequest = null;
        this.form.patchValue({ beneficiaryName: '', toIban: '', amount: 0, reference: '' });
        this.form.markAsPristine();
        void this.router.navigate(['/accounts']);
      }
    });
    effect(() => {
      if (this.nameMismatch()) untracked(() => this.confirmNameMismatch());
    });
  }

  ngOnInit(): void {
    this.store.dispatch(clearPayments());
    this.store.dispatch(loadAccounts());
  }

  updateCurrency(iban: string): void {
    this.selectedIban.set(iban);
    const account = this.accounts().find((account: Account) => account.iban === iban);
    this.selectedCurrency.set(account?.currency ?? 'EUR');
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    this.send({
      ...value,
      toIban: value.toIban.replace(/\s/g, '').toUpperCase(),
      currency: this.selectedCurrency(),
    });
  }

  /** The bank rejected the beneficiary name; the payer decides in a dialog whether to send the payment regardless. */
  private confirmNameMismatch(): void {
    const request = this.lastRequest;
    if (!request) return;
    const { beneficiaryName, toIban, amount, currency } = request;
    this.dialog
      .open(NameMismatchDialogComponent, {
        data: { beneficiaryName, toIban, amount, currency },
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) this.sendAnyway();
      });
  }

  /** The payer was warned that the name does not match the account holder and sends the payment regardless. */
  sendAnyway(): void {
    if (!this.lastRequest || this.submitting()) return;
    this.send({ ...this.lastRequest, proceedDespiteNameMismatch: true });
  }

  private send(request: PaymentRequest): void {
    this.lastRequest = request;
    this.store.dispatch(submitPayment({ request, idempotencyKey: this.idempotency.keyFor(request) }));
  }
}
