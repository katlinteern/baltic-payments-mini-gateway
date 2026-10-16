import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PageResponse } from '@shared/models/page-response.model';
import { Transaction } from '@shared/models/transaction.model';
import { startOfLocalDay, startOfNextLocalDay } from './date-range';
import { TransactionFilters } from './transaction-filters.model';

@Injectable({ providedIn: 'root' })
export class TransactionsApiService {
  private readonly http = inject(HttpClient);

  list(filters: TransactionFilters) {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    if (filters.iban) params = params.set('iban', filters.iban);
    // The user's calendar days are sent as instants, so "a day" means the same on the server as on screen.
    if (filters.dateFrom) params = params.set('from', startOfLocalDay(filters.dateFrom));
    if (filters.dateTo) params = params.set('to', startOfNextLocalDay(filters.dateTo));
    return this.http.get<PageResponse<Transaction>>('/api/transactions', { params });
  }
}
