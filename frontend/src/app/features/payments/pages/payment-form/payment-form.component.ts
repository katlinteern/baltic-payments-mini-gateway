import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Account } from '@shared/models/account.model';
import { SectionHeadComponent } from '@shared/ui';
import { PaymentRequest } from '@features/payments/state/payment.model';
import { currencyOf, PaymentFormGroup, toPaymentRequest } from './payment-form';

@Component({
  selector: 'app-payment-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    SectionHeadComponent,
  ],
  templateUrl: './payment-form.component.html',
  styleUrl: './payment-form.component.scss',
})
export class PaymentFormComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  readonly form = input.required<PaymentFormGroup>();
  readonly accounts = input.required<readonly Account[]>();
  readonly busy = input(false);
  readonly submitted = output<PaymentRequest>();

  private readonly selectedIban = signal('');
  readonly selectedAccount = computed(() => this.accounts().find((account) => account.iban === this.selectedIban()));
  readonly selectedCurrency = computed(() => currencyOf(this.accounts(), this.selectedIban()));

  ngOnInit(): void {
    const control = this.form().controls.fromIban;
    this.selectedIban.set(control.value);
    control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((iban) => this.selectedIban.set(iban));
  }

  submit(): void {
    if (this.busy()) return;
    const request = toPaymentRequest(this.form(), this.accounts());
    if (request) this.submitted.emit(request);
  }
}
