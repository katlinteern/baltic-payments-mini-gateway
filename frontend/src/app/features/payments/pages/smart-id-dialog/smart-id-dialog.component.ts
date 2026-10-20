import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Store } from '@ngrx/store';
import { selectIntent } from '@features/payments/state/payments.selectors';

/**
 * Blocks the payment page while the payer approves it in the authentication app: shows the code to compare and the
 * time left. It closes with {@code true} when the payer cancels; the page closes it when the payment moves on.
 */
@Component({
  selector: 'app-smart-id-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatButtonModule, MatDialogModule, MatProgressBarModule],
  templateUrl: './smart-id-dialog.component.html',
  styleUrl: './smart-id-dialog.component.scss',
})
export class SmartIdDialogComponent {
  readonly intent = inject(Store).selectSignal(selectIntent);
  private readonly now = signal(Date.now());
  /** Time the payer has left to approve, as m:ss; never negative. */
  readonly countdown = computed(() => {
    const expiresAt = this.intent()?.expiresAt;
    const seconds = expiresAt ? Math.max(0, Math.ceil((Date.parse(expiresAt) - this.now()) / 1000)) : 0;
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}
