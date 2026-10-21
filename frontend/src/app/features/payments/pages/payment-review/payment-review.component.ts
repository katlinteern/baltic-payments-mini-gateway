import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AlertComponent, SectionHeadComponent } from '@shared/ui';
import { Payment } from '@features/payments/state/payment.model';

@Component({
  selector: 'app-payment-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AlertComponent, DecimalPipe, MatButtonModule, MatCardModule, MatCheckboxModule, SectionHeadComponent],
  templateUrl: './payment-review.component.html',
  styleUrl: './payment-review.component.scss',
})
export class PaymentReviewComponent {
  readonly payment = input.required<Payment>();
  readonly busy = input(false);
  readonly waiting = input(false);
  readonly confirmed = output<boolean>();
  readonly edited = output<void>();

  readonly acknowledged = signal(false);
  readonly mustAcknowledge = computed(() => this.payment().nameCheck === 'NO_MATCH' && !this.acknowledged());

  confirm(): void {
    if (this.busy() || this.mustAcknowledge()) return;
    this.confirmed.emit(this.acknowledged());
  }

  edit(): void {
    this.acknowledged.set(false);
    this.edited.emit();
  }
}
