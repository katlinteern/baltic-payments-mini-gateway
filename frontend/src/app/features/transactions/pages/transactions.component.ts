import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Store } from '@ngrx/store';
import {
  AlertComponent,
  EmptyStateComponent,
  PageComponent,
  SectionHeadComponent,
  StatusPillComponent,
} from '@shared/ui';
import { TransactionFilters } from '../state/transaction-filters.model';
import { loadTransactions } from '../state/transactions.actions';
import {
  selectTransactions,
  selectTransactionsError,
  selectTransactionsLoading,
  selectTransactionsTotal,
} from '../state/transactions.selectors';

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
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    PageComponent,
    SectionHeadComponent,
    StatusPillComponent,
  ],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss',
})
export class TransactionsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly transactions = this.store.selectSignal(selectTransactions);
  readonly total = this.store.selectSignal(selectTransactionsTotal);
  readonly loading = this.store.selectSignal(selectTransactionsLoading);
  readonly error = this.store.selectSignal(selectTransactionsError);
  readonly page = signal(0);
  readonly pageSize = signal(10);
  readonly columns = ['date', 'direction', 'reference', 'amount', 'status'];
  readonly filters = this.fb.nonNullable.group({ iban: [''], status: [''], dateFrom: [''], dateTo: [''] });

  ngOnInit(): void {
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
      ...this.filters.getRawValue(),
      iban: this.filters.controls.iban.value.trim(),
      page: this.page(),
      size: this.pageSize(),
    };
    this.store.dispatch(loadTransactions({ filters }));
  }
}
