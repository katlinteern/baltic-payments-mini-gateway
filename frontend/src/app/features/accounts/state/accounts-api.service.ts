import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Account } from '../../../shared/models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountsApiService {
  private readonly http = inject(HttpClient);

  list() {
    return this.http.get<Account[]>('/api/accounts');
  }
}