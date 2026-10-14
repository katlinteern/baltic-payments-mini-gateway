import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Account, PageResponse, PaymentRequest, Transaction, TransactionFilters } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  accounts() { return this.http.get<Account[]>('/api/accounts'); }
  pay(body: PaymentRequest, key: string) {
    return this.http.post<Transaction>('/api/payments', body, { headers: new HttpHeaders({ 'Idempotency-Key': key }) });
  }
  transactions(filters: TransactionFilters) {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    if (filters.iban) params = params.set('iban', filters.iban);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
    if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
    return this.http.get<PageResponse<Transaction>>('/api/transactions', { params });
  }
}
