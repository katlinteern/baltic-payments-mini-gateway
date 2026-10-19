import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Transaction } from '@shared/models/transaction.model';
import { Direction, DIRECTION_SIGN } from '../state/transaction-direction';

export interface TransactionDetailDialogData {
  transaction: Transaction;
  direction: Direction;
  balanceAfter: number | null;
}

@Component({
  selector: 'app-transaction-detail-dialog',
  standalone: true,
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Payment details</h2>
    <mat-dialog-content>
      <p class="amount" [class]="data.direction">{{ sign }}{{ tx.amount | number: '1.2-2' }} {{ tx.currency }}</p>
      <dl>
        <dt>From</dt>
        <dd>
          <strong>{{ tx.fromName || '—' }}</strong>
          <span>{{ tx.fromIban }}</span>
        </dd>
        <dt>To</dt>
        <dd>
          <strong>{{ tx.toName || tx.beneficiaryName || '—' }}</strong>
          <span>{{ tx.toIban }}</span>
        </dd>
        <dt>Date</dt>
        <dd>{{ tx.createdAt | date: 'dd.MM.yyyy HH:mm' }}</dd>
        <dt>Reference</dt>
        <dd>{{ tx.reference || '—' }}</dd>
        <dt>Status</dt>
        <dd>{{ tx.status }}</dd>
        @if (data.balanceAfter !== null) {
          <dt>Balance after</dt>
          <dd>{{ data.balanceAfter | number: '1.2-2' }} {{ tx.currency }}</dd>
        }
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" mat-dialog-close cdkFocusInitial>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .amount {
      margin: 0 0 12px;
      font-size: 28px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .amount.incoming {
      color: var(--success);
    }
    .amount.outgoing {
      color: var(--danger);
    }
    dl {
      display: grid;
      grid-template-columns: 110px 1fr;
      gap: 10px 12px;
      margin: 0;
    }
    dt {
      color: var(--muted);
    }
    dd {
      margin: 0;
    }
    dd span {
      display: block;
      color: var(--muted);
      font-size: 13px;
      letter-spacing: 0.02em;
    }
  `,
})
export class TransactionDetailDialogComponent {
  readonly data = inject<TransactionDetailDialogData>(MAT_DIALOG_DATA);
  readonly tx = this.data.transaction;
  readonly sign = DIRECTION_SIGN[this.data.direction];
}
