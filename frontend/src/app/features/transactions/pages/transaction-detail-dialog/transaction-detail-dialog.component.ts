import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Transaction } from '@shared/models/transaction.model';
import { Direction } from '@features/transactions/state/transaction-direction';

export interface TransactionDetailDialogData {
  transaction: Transaction;
  direction: Direction;
  balanceAfter: number | null;
}

@Component({
  selector: 'app-transaction-detail-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatDialogModule],
  templateUrl: './transaction-detail-dialog.component.html',
  styleUrl: './transaction-detail-dialog.component.scss',
})
export class TransactionDetailDialogComponent {
  readonly data = inject<TransactionDetailDialogData>(MAT_DIALOG_DATA);
  readonly tx = this.data.transaction;
}
