import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PageResponse } from '../../../shared/models/page-response.model';
import { Transaction } from '../../../shared/models/transaction.model';
import { TransactionFilters } from './transaction-filters.model';

@Injectable({ providedIn: 'root' })
export class TransactionsApiService {
  private readonly http = inject(HttpClient);

  list(filters: TransactionFilters) {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    if (filters.iban) params = params.set('iban', filters.iban);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
    if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
    return this.http.get<PageResponse<Transaction>>('/api/transactions', { params });
  }
}