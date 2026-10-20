import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Store } from '@ngrx/store';
import { loadAccounts, selectAccounts } from '@features/accounts/state';
import { Transaction } from '@shared/models/transaction.model';
import { AlertComponent, EmptyStateComponent, PageComponent } from '@shared/ui';
import { TransactionDetailDialogComponent } from './transaction-detail-dialog.component';
import { TransactionFilters } from '../state/transaction-filters.model';
import { Direction, DIRECTION_SIGN, directionOf } from '../state/transaction-direction';
import { loadTransactions } from '../state/transactions.actions';
import {
  selectTransactions,
  selectTransactionsError,
  selectTransactionsLoading,
  selectTransactionsTotal,
} from '../state/transactions.selectors';

function counterpartyOf(transaction: Transaction, direction: Direction): string {
  const from = transaction.fromName || transaction.fromIban;
  const to = transaction.toName || transaction.toIban;
  if (direction === 'incoming') return from;
  return direction === 'other' ? `${from} → ${to}` : to;
}

function toIsoDay(date: Date | null): string {
  return date ? date.toLocaleDateString('sv-SE') : '';
}

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [
    AlertComponent,
    DatePipe,
    DecimalPipe,
    EmptyStateComponent,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    PageComponent,
  ],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss',
})
export class TransactionsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly transactions = this.store.selectSignal(selectTransactions);
  private readonly accounts = this.store.selectSignal(selectAccounts);
  private readonly ownIbans = computed(() => new Set(this.accounts().map((account) => account.iban)));
  /** The API lists a transfer between the user's own accounts as two rows: a debit and a credit. */
  readonly rows = computed(() =>
    this.transactions().map((transaction) => {
      if (transaction.entry) {
        const outgoing = transaction.entry === 'DEBIT';
        return this.row(
          transaction,
          outgoing ? 'outgoing' : 'incoming',
          outgoing ? transaction.fromBalanceAfter : transaction.toBalanceAfter,
        );
      }
      const direction = directionOf(transaction, this.ownIbans());
      return this.row(
        transaction,
        direction,
        direction === 'incoming' ? transaction.toBalanceAfter : transaction.fromBalanceAfter,
      );
    }),
  );
  readonly total = this.store.selectSignal(selectTransactionsTotal);
  readonly loading = this.store.selectSignal(selectTransactionsLoading);
  readonly error = this.store.selectSignal(selectTransactionsError);
  readonly page = signal(0);
  readonly pageSize = signal(10);
  readonly columns = ['date', 'direction', 'reference', 'amount', 'balance'];
  readonly filters = this.fb.nonNullable.group({
    query: [''],
    dateFrom: [null as Date | null],
    dateTo: [null as Date | null],
  });

  private row(transaction: Transaction, direction: Direction, balanceAfter: number | null) {
    return {
      transaction,
      direction,
      sign: DIRECTION_SIGN[direction],
      counterparty: counterpartyOf(transaction, direction),
      balanceAfter,
    };
  }

  openDetails(transaction: Transaction, direction: Direction, balanceAfter: number | null): void {
    this.dialog.open(TransactionDetailDialogComponent, {
      data: { transaction, direction, balanceAfter },
      width: '480px',
      maxWidth: '95vw',
    });
  }

  ngOnInit(): void {
    this.store.dispatch(loadAccounts());
    this.load();
  }
  applyFilters(): void {
    this.page.set(0);
    this.load();
  }
  changePage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  private load(): void {
    const filters: TransactionFilters = {
      query: this.filters.controls.query.value.trim(),
      dateFrom: toIsoDay(this.filters.controls.dateFrom.value),
      dateTo: toIsoDay(this.filters.controls.dateTo.value),
      page: this.page(),
      size: this.pageSize(),
    };
    this.store.dispatch(loadTransactions({ filters }));
  }
}
